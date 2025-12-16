import sqlite3
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import glob
import os
import sys

# 设置绘图风格
try:
    import seaborn as sns
    sns.set_theme(style="whitegrid")
except ImportError:
    plt.style.use('ggplot')

# --- 配置字体 ---
system_fonts = ['Helvetica', 'Arial', 'sans-serif']
plt.rcParams['font.sans-serif'] = system_fonts
plt.rcParams['axes.unicode_minus'] = False

def get_market_data(db_path, max_days=250):
    """读取数据库市场数据"""
    if not os.path.exists(db_path):
        return None
    try:
        conn = sqlite3.connect(db_path)
        query = f"SELECT day, close FROM market_log WHERE day <= {max_days} ORDER BY day ASC"
        df = pd.read_sql_query(query, conn)
        conn.close()
        return df
    except Exception as e:
        print(f"读取失败 {db_path}: {e}")
        return None

def format_large_num(x, pos):
    """Y轴格式化函数：添加千位分隔符"""
    return '{:,.0f}'.format(x)

def plot_market_trends_parallel(db_files):
    """
    并列绘制三个折线图
    """
    # 限制最多处理3个文件，防止布局混乱
    files_to_plot = db_files[:3]
    num_files = len(files_to_plot)

    if num_files == 0:
        print("错误: 未找到数据库文件。")
        return

    print(f"检测到 {len(db_files)} 个文件，将绘制前 {num_files} 个...")

    # 创建 1行3列 的布局，增加总宽度
    fig, axes = plt.subplots(1, 3, figsize=(20, 6))

    # 确保 axes 是可迭代对象（即使只有一个文件）
    if num_files == 1:
        axes = [axes]

    # 预定义颜色列表，让不同的图看起来略有区分
    colors = ['#1f77b4', '#ff7f0e', '#2ca02c']

    for i, db_file in enumerate(files_to_plot):
        ax = axes[i]
        print(f"正在处理 [{i+1}/{num_files}]: {db_file} ...")

        df = get_market_data(db_file, max_days=250)

        # --- 标签重命名逻辑 ---
        label_name = os.path.splitext(os.path.basename(db_file))[0]
        if label_name == "具有IPO，无涨跌停":
            label_name = "IPO (Normal)"
        elif label_name == "无IPO，有涨跌停，放宽持股总数限制，估值模型调整":
            label_name = "Forced Allocation"
        elif label_name == "具有IPO，有涨跌停，且初始设置60%的公司处于亏损":
            label_name = "IPO (Unprofitable)"

        if df is not None:
            # 绘图
            ax.plot(df['day'], df['close'], color=colors[i], linewidth=2)

            # --- 关键修改：重点标注纵轴 ---
            # 1. 设置标题
            ax.set_title(label_name, fontsize=14, fontweight='bold', pad=15)

            # 2. 设置轴标签
            ax.set_xlabel('Trading Day', fontsize=11)
            if i == 0: # 只在第一张图显示Y轴名称，保持整洁，或者每个都显示
                ax.set_ylabel('Market Index (Close)', fontsize=12)

            # 3. 格式化 Y 轴刻度 (添加逗号分隔，例如 100,000)
            ax.yaxis.set_major_formatter(ticker.FuncFormatter(format_large_num))

            # 4. 增大刻度字体，使其醒目
            ax.tick_params(axis='y', labelsize=11, labelcolor='black')
            ax.tick_params(axis='x', labelsize=10)

            # 5. 网格线
            ax.grid(True, linestyle='--', alpha=0.6)

            # 6. 显示当前子图的最大/最小值，辅助阅读
            max_val = df['close'].max()
            min_val = df['close'].min()
            # 在图表角落标注范围
            info_text = f"Max: {max_val:,.0f}\nMin: {min_val:,.0f}"
            ax.text(0.05, 0.95, info_text, transform=ax.transAxes,
                    fontsize=10, verticalalignment='top',
                    bbox=dict(boxstyle='round', facecolor='white', alpha=0.8))

        else:
            ax.text(0.5, 0.5, 'Data Load Error', ha='center')

    # 隐藏多余的空子图 (如果文件少于3个)
    for j in range(num_files, 3):
        if hasattr(axes, '__getitem__'): # 确保是数组
            fig.delaxes(axes[j])

    plt.tight_layout()
    output_img = 'market_parallel_comparison.png'
    plt.savefig(output_img, dpi=300)
    print(f"\n绘图完成！图片已保存为: {output_img}")
    plt.show()

if __name__ == "__main__":
    search_path = os.path.join("output", "*.db")
    db_files = glob.glob(search_path)

    if not db_files:
        db_files = glob.glob("*.db")

    if len(sys.argv) > 1:
        db_files = sys.argv[1:]

    # 为了保证对比顺序一致，可以先排个序
    db_files.sort()

    if not db_files:
        print("未找到任何 .db 数据库文件。")
    else:
        plot_market_trends_parallel(db_files)