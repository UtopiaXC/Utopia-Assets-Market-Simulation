#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Empirical Stylized Facts Validation for Utopia Assets Market Simulation
========================================================================
Generates publication-quality single-panel figures for academic papers:
  1. Mean Reversion: Price index trajectory showing convergence to equilibrium around Day 100
  2. Fat Tails: Normal Q-Q Plot of log returns demonstrating leptokurtosis
  3. Volatility Clustering: Autocorrelation of raw vs absolute and squared returns

Usage:
  python Analyze/plot_validation.py [path_to_db] [options]

Examples:
  python Analyze/plot_validation.py
  python Analyze/plot_validation.py output/fast_baseline_3000_days.db
  python Analyze/plot_validation.py output/fast_baseline_3000_days.db --days 300
"""

import os
import sys
import glob
import argparse
import sqlite3
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy import stats

try:
    from statsmodels.tsa.stattools import acf as sm_acf
    HAS_STATSMODELS = True
except ImportError:
    HAS_STATSMODELS = False


# =============================================================================
# Matplotlib Publication Aesthetics Configuration
# =============================================================================
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Helvetica', 'sans-serif']
plt.rcParams['axes.unicode_minus'] = False
plt.rcParams['axes.edgecolor'] = '#333333'
plt.rcParams['axes.linewidth'] = 0.9
plt.rcParams['grid.color'] = '#cccccc'
plt.rcParams['grid.linestyle'] = '--'
plt.rcParams['grid.linewidth'] = 0.6
plt.rcParams['grid.alpha'] = 0.55
plt.rcParams['axes.titlesize'] = 12
plt.rcParams['axes.titleweight'] = 'bold'
plt.rcParams['axes.labelsize'] = 11
plt.rcParams['xtick.labelsize'] = 10
plt.rcParams['ytick.labelsize'] = 10
plt.rcParams['legend.fontsize'] = 10
plt.rcParams['legend.framealpha'] = 0.92
plt.rcParams['legend.edgecolor'] = '#d0d0d0'


# =============================================================================
# Data Loading Utilities
# =============================================================================
def load_price_series(db_path, stock_id=None):
    """
    Load price series from SQLite database.
    If stock_id is None or < 0, loads the Market Composite Index from market_daily.
    Otherwise, loads the specific stock close prices from stock_daily.
    """
    if not os.path.exists(db_path):
        raise FileNotFoundError(f"Database file not found: {db_path}")

    conn = sqlite3.connect(db_path)
    try:
        if stock_id is not None and stock_id >= 0:
            query = f"SELECT day, close FROM stock_daily WHERE stock_id = {stock_id} AND close > 0 ORDER BY day ASC"
            df = pd.read_sql_query(query, conn)
        else:
            query = "SELECT day, close FROM market_daily WHERE close > 0 ORDER BY day ASC"
            df = pd.read_sql_query(query, conn)

            # Fallback if market_daily is empty
            if df.empty:
                first_stock = pd.read_sql_query("SELECT id FROM stock LIMIT 1", conn)
                if not first_stock.empty:
                    sid = first_stock['id'].iloc[0]
                    query = f"SELECT day, close FROM stock_daily WHERE stock_id = {sid} AND close > 0 ORDER BY day ASC"
                    df = pd.read_sql_query(query, conn)
    finally:
        conn.close()

    if df.empty or len(df) < 10:
        raise ValueError(f"Insufficient price records in database {db_path}.")

    return df['day'].values, df['close'].values


def compute_acf(series, nlags=40):
    """Compute sample autocorrelation function up to nlags."""
    if HAS_STATSMODELS:
        return sm_acf(series, nlags=nlags, fft=True)
    n = len(series)
    mean = np.mean(series)
    var = np.var(series)
    if var < 1e-12:
        return np.zeros(nlags + 1)
    res = [1.0]
    for lag in range(1, nlags + 1):
        if lag >= n:
            res.append(0.0)
            continue
        c = np.sum((series[lag:] - mean) * (series[:-lag] - mean)) / (n * var)
        res.append(c)
    return np.array(res)


# =============================================================================
# Standalone Single-Plot Verification Figures
# =============================================================================
def plot_mean_reversion(days, prices, out_path_base, max_days=300):
    """
    Figure 1: Mean Reversion
    Single plot displaying market index price convergence to equilibrium around Day 100.
    Strictly NO parentheses anywhere in labels or titles.
    """
    fig, ax = plt.subplots(figsize=(7.5, 4.8))

    mask = days <= max_days
    sub_days = days[mask]
    sub_prices = prices[mask]

    # Calculate post-convergence equilibrium mean (Day 100 onwards)
    post_100_mask = sub_days >= 100
    if np.any(post_100_mask):
        eq_mean = np.mean(sub_prices[post_100_mask])
    else:
        eq_mean = np.mean(sub_prices)

    ax.plot(sub_days, sub_prices, color='#1f77b4', linewidth=1.6, label='Market Price Index')
    ax.axhline(eq_mean, color='#d62728', linestyle='--', linewidth=1.3, label=f'Equilibrium Mean {eq_mean:.0f}')
    ax.axvline(100, color='#2ca02c', linestyle=':', linewidth=1.3, label='Day 100 Mean Reversion')

    ax.set_title('Market Price Index Mean Reversion')
    ax.set_xlabel('Trading Day')
    ax.set_ylabel('Market Index')
    ax.grid(True)
    ax.legend(loc='upper right')

    plt.tight_layout()
    png_path = f"{out_path_base}_mean_reversion.png"
    pdf_path = f"{out_path_base}_mean_reversion.pdf"
    plt.savefig(png_path, dpi=300)
    plt.savefig(pdf_path)
    plt.close()
    print(f"Generated: {png_path} and {pdf_path}")


def plot_fat_tails(prices, out_path_base):
    """
    Figure 2: Fat Tails Evidence
    Single Normal Q-Q Plot of standardized returns clearly displaying leptokurtic tails.
    Strictly NO parentheses anywhere in labels or titles.
    """
    fig, ax = plt.subplots(figsize=(7.5, 4.8))

    returns = np.diff(np.log(prices))
    n = len(returns)
    mean_r = np.mean(returns)
    std_r = np.std(returns, ddof=1)
    std_returns = (returns - mean_r) / std_r

    sorted_std = np.sort(std_returns)
    p_vals = (np.arange(1, n + 1) - 0.5) / n
    theo_quantiles = stats.norm.ppf(p_vals)

    min_q = min(theo_quantiles[0], sorted_std[0])
    max_q = max(theo_quantiles[-1], sorted_std[-1])

    ax.scatter(theo_quantiles, sorted_std, color='#1f77b4', s=14, alpha=0.6, label='Sample Quantile')
    ax.plot([min_q, max_q], [min_q, max_q], color='#d62728', linestyle='--', linewidth=1.6, label='45 Degree Reference Line')

    # Objective minimal stats box (strictly no parentheses)
    kurt = stats.kurtosis(returns)
    stats_box = (
        f"Excess Kurtosis: {kurt:.2f}\n"
        f"Sample Size: {n}"
    )
    ax.text(0.04, 0.78, stats_box, transform=ax.transAxes, verticalalignment='top',
            bbox=dict(boxstyle='round,pad=0.5', facecolor='white', edgecolor='#cccccc', alpha=0.92))

    ax.set_title('Normal Q-Q Plot of Log Returns')
    ax.set_xlabel('Theoretical Normal Quantile')
    ax.set_ylabel('Standardized Sample Quantile')
    ax.grid(True)
    ax.legend(loc='upper left')

    plt.tight_layout()
    png_path = f"{out_path_base}_fat_tails.png"
    pdf_path = f"{out_path_base}_fat_tails.pdf"
    plt.savefig(png_path, dpi=300)
    plt.savefig(pdf_path)
    plt.close()
    print(f"Generated: {png_path} and {pdf_path}")


def plot_volatility_clustering(prices, out_path_base, nlags=40):
    """
    Figure 3: Volatility Clustering Evidence
    Single plot contrasting ACF of raw returns vs absolute and squared returns.
    Strictly NO parentheses anywhere in labels or titles.
    """
    fig, ax = plt.subplots(figsize=(7.5, 4.8))

    returns = np.diff(np.log(prices))
    n = len(returns)
    residuals = returns - np.mean(returns)
    sq_residuals = residuals ** 2
    abs_returns = np.abs(returns)

    raw_acf = compute_acf(returns, nlags=nlags)[1:]
    abs_acf = compute_acf(abs_returns, nlags=nlags)[1:]
    sq_acf = compute_acf(sq_residuals, nlags=nlags)[1:]
    lags = np.arange(1, nlags + 1)
    ci_band = 1.96 / np.sqrt(n)

    ax.plot(lags, abs_acf, marker='o', markersize=4, color='#2ca02c', linewidth=1.8, label='Absolute Returns ACF')
    ax.plot(lags, sq_acf, marker='s', markersize=3.5, color='#ff7f0e', linestyle='--', linewidth=1.6, label='Squared Returns ACF')
    ax.bar(lags, raw_acf, color='#1f77b4', alpha=0.5, width=0.6, label='Raw Returns ACF')
    ax.axhline(ci_band, color='#888888', linestyle=':', linewidth=1.2, label='95% Bartlett Confidence Band')
    ax.axhline(-ci_band, color='#888888', linestyle=':', linewidth=1.2)
    ax.axhline(0, color='black', linewidth=0.8)

    ax.set_title('Autocorrelation of Raw and Non-Linear Returns')
    ax.set_xlabel('Lag')
    ax.set_ylabel('Autocorrelation')
    max_val = max(0.5, np.max(abs_acf) * 1.15 if len(abs_acf) > 0 else 0.5)
    ax.set_ylim(-0.1, max(0.45, max_val))
    ax.grid(True)
    ax.legend(loc='upper right')

    plt.tight_layout()
    png_path = f"{out_path_base}_volatility_clustering.png"
    pdf_path = f"{out_path_base}_volatility_clustering.pdf"
    plt.savefig(png_path, dpi=300)
    plt.savefig(pdf_path)
    plt.close()
    print(f"Generated: {png_path} and {pdf_path}")


# =============================================================================
# Main Entry Point
# =============================================================================
def main():
    parser = argparse.ArgumentParser(
        description="Generate concise academic publication figures for Stylized Facts validation."
    )
    parser.add_argument(
        'db_path',
        nargs='?',
        default='output/fast_baseline_3000_days.db',
        help="Path to SQLite simulation database file. Default: output/fast_baseline_3000_days.db"
    )
    parser.add_argument(
        '--db',
        dest='db_flag',
        default=None,
        help="Alternative flag for database path."
    )
    parser.add_argument(
        '-s', '--stock-id',
        type=int,
        default=None,
        help="Specific stock ID to analyze. Default: Market Composite Index."
    )
    parser.add_argument(
        '-o', '--out-dir',
        default='Analyze/figures',
        help="Output directory to save generated figures. Default: Analyze/figures."
    )
    parser.add_argument(
        '-d', '--days',
        type=int,
        default=300,
        help="Trading days horizon for Mean Reversion plot. Default: 300."
    )
    parser.add_argument(
        '--prefix',
        default=None,
        help="Prefix for figure filenames. Default: database basename."
    )

    args = parser.parse_args()

    # Determine database path
    target_db = args.db_flag or args.db_path
    if not os.path.isfile(target_db):
        candidates = glob.glob(os.path.join("output", "*.db")) + glob.glob("*.db")
        if candidates:
            target_db = sorted(candidates)[0]
            print(f"Specified database not found. Using: {target_db}")
        else:
            print(f"Error: Database file does not exist: {target_db}")
            sys.exit(1)

    os.makedirs(args.out_dir, exist_ok=True)
    db_base = args.prefix or os.path.splitext(os.path.basename(target_db))[0]
    out_base = os.path.join(args.out_dir, db_base)

    print("=" * 70)
    print("Stylized Facts Single-Plot Paper Figures Generator")
    print("=" * 70)
    print(f"Database:  {target_db}")
    print(f"Target:    {'Stock #' + str(args.stock_id) if args.stock_id is not None else 'Market Composite Index'}")
    print(f"Output:    {args.out_dir}/")
    print(f"Horizon:   {args.days} days for Mean Reversion")
    print("-" * 70)

    # Load data
    days, prices = load_price_series(target_db, stock_id=args.stock_id)
    print(f"Loaded {len(prices)} trading days.")

    # 1. Mean Reversion
    print("\n[1/3] Generating Mean Reversion Plot...")
    plot_mean_reversion(days, prices, out_base, max_days=args.days)

    # 2. Fat Tails
    print("[2/3] Generating Fat Tails Plot...")
    plot_fat_tails(prices, out_base)

    # 3. Volatility Clustering
    print("[3/3] Generating Volatility Clustering Plot...")
    plot_volatility_clustering(prices, out_base)

    print("\n" + "=" * 70)
    print(f"All 3 paper figures successfully generated in: {args.out_dir}")
    print("=" * 70)


if __name__ == "__main__":
    main()
