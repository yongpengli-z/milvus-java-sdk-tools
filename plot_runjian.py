from pathlib import Path
import sys

sys.path.insert(0, str(Path(sys.executable).parent.parent.parent))
from daimon_runtime import setup_plot
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates

setup_plot()

WS = Path("/Users/yongpengli/Documents/GitHub/milvus-java-sdk-toos")
df = pd.read_csv(WS / "runjian_002929_price.csv")
df["time"] = pd.to_datetime(df["time"], format="%Y%m%d")
df = df.sort_values("time").reset_index(drop=True)

# 均线
df["ma20"] = df["close"].rolling(20).mean()
df["ma60"] = df["close"].rolling(60).mean()

fig, (ax1, ax2) = plt.subplots(
    2, 1, figsize=(12, 7), sharex=True,
    gridspec_kw={"height_ratios": [3, 1]}, constrained_layout=True,
)

ax1.plot(df["time"], df["close"], color="#c0392b", lw=1.6, label="收盘价")
ax1.plot(df["time"], df["ma20"], color="#e67e22", lw=1.0, label="MA20")
ax1.plot(df["time"], df["ma60"], color="#2980b9", lw=1.0, label="MA60")
ax1.set_title("润建股份（002929.SZ）近一年走势（前复权）", fontsize=14)
ax1.set_ylabel("价格（元）")
ax1.legend(loc="best", fontsize=9)
ax1.grid(alpha=0.3)

colors = ["#c0392b" if c >= o else "#27ae60" for c, o in zip(df["close"], df["open"])]
ax2.bar(df["time"], df["volume"] / 1e4, color=colors, width=1.2)
ax2.set_ylabel("成交量（万手）")
ax2.grid(alpha=0.3)
ax2.xaxis.set_major_locator(mdates.MonthLocator())
ax2.xaxis.set_major_formatter(mdates.DateFormatter("%Y-%m"))

out = WS / "runjian_002929_trend.png"
fig.savefig(out, dpi=200)
plt.close(fig)

# 关键统计
last = df.iloc[-1]
first = df.iloc[0]
hi = df.loc[df["close"].idxmax()]
lo = df.loc[df["close"].idxmin()]
chg = (last["close"] / first["close"] - 1) * 100
print(f"区间: {df['time'].iloc[0].date()} ~ {df['time'].iloc[-1].date()}, 共{len(df)}个交易日")
print(f"最新收盘: {last['close']:.2f} 元 ({last['time'].date()})")
print(f"区间涨跌幅: {chg:+.1f}%")
print(f"区间最高收盘: {hi['close']:.2f} 元 ({hi['time'].date()})")
print(f"区间最低收盘: {lo['close']:.2f} 元 ({lo['time'].date()})")
print(f"saved: {out}")
