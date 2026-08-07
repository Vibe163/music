#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
音乐文件 MD5 查重工具
====================

递归扫描指定文件夹中的音乐文件，用 MD5 校验内容，找出所有重复文件
（内容完全相同的文件），并输出：
  1. 控制台摘要（重复组数、重复文件数、可节省空间）
  2. 文本报告（每个重复组列出所有文件路径 + 大小 + MD5）

用法:
    python music_duplicate_checker.py [文件夹1 [文件夹2 ...]] [选项]

示例:
    python music_duplicate_checker.py D:\\Music
    python music_duplicate_checker.py D:\\Music D:\\Music2 --threads 8
    python music_duplicate_checker.py D:\\Music --out D:\\report.txt

选项:
    --threads N         MD5 计算线程数（默认 = CPU 核数，硬盘快时开多线程提速）
    --no-size-filter    关闭大小预筛（默认只对"大小相同的文件"算 MD5，快很多且同样准确）
    --min-size MB       忽略小于该 MB 的文件（默认 0）
    --ext a,b,c         自定义扩展名，逗号分隔（默认覆盖常见音频格式）
    --out FILE          报告文件输出路径（默认与第一个扫描目录同名，放在当前目录）

原理说明:
    - 先按"文件大小"分组（同一内容的文件大小必然相同），不同大小直接排除，避免对
      超大文件做无谓的 MD5 计算，通常能省掉 90%+ 的计算量。
    - 对同大小候选文件流式分块（1MB）计算 MD5，内存占用恒定，不担心大文件。
    - MD5 相同即判定内容重复；每个重复组中保留 1 个，其余都是可删除的冗余。
"""

import argparse
import hashlib
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# 常见音频扩展名（大小写不敏感）
DEFAULT_EXTS = {
    ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".wma",
    ".mid", ".midi", ".amr", ".mka", ".aiff", ".aif", ".ape", ".wv",
    ".mp2", ".alac", ".mpc", ".tta", ".dff", ".dsf",
}

CHUNK_SIZE = 1024 * 1024  # 1MB 分块


def md5_of_file(path: str) -> str:
    """流式计算文件 MD5，内存占用恒定"""
    h = hashlib.md5()
    with open(path, "rb") as f:
        while True:
            block = f.read(CHUNK_SIZE)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def collect_audio_files(roots, exts, min_bytes):
    """递归收集所有符合条件的音频文件，返回 [(路径, 大小)]"""
    files = []
    for root in roots:
        rp = Path(root)
        if not rp.exists():
            print(f"[警告] 目录不存在: {root}")
            continue
        if not rp.is_dir():
            print(f"[警告] 不是文件夹: {root}")
            continue
        for p in rp.rglob("*"):
            try:
                if p.is_file() and p.suffix.lower() in exts:
                    size = p.stat().st_size
                    if size >= min_bytes:
                        files.append((str(p), size))
            except OSError:
                continue
    return files


def human_size(n: int) -> str:
    """字节数 -> 可读大小"""
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024 or unit == "TB":
            return f"{n:.2f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024


def main():
    ap = argparse.ArgumentParser(
        description="音乐文件 MD5 查重工具（递归扫描，找出内容重复的文件）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="示例: python music_duplicate_checker.py D:\\Music --threads 8",
    )
    ap.add_argument("paths", nargs="*", help="要扫描的文件夹（可多个），不填则扫描当前目录")
    ap.add_argument("--threads", type=int, default=os.cpu_count() or 4, help="MD5 线程数（默认 CPU 核数）")
    ap.add_argument("--no-size-filter", action="store_true", help="关闭大小预筛，对全部文件算 MD5")
    ap.add_argument("--min-size", type=float, default=0.0, help="忽略小于该 MB 的文件")
    ap.add_argument("--ext", default=None, help="自定义扩展名，逗号分隔，如 mp3,flac")
    ap.add_argument("--out", default=None, help="报告输出路径（默认 <目录名>_duplicates_report.txt）")
    args = ap.parse_args()

    roots = args.paths or [str(Path.cwd())]
    exts = {("." + e.strip().lower().lstrip(".")) for e in args.ext.split(",") if e.strip()} if args.ext else DEFAULT_EXTS
    min_bytes = int(args.min_size * 1024 * 1024)

    print("=" * 70)
    print("音乐文件 MD5 查重")
    print("=" * 70)
    print(f"扫描目录 : {', '.join(roots)}")
    print(f"音频格式 : {', '.join(sorted(exts))}")

    files = collect_audio_files(roots, exts, min_bytes)
    print(f"共发现   : {len(files)} 个音频文件")
    if not files:
        print("没有找到任何音乐文件。")
        return

    # ---- 第一步：按文件大小分组预筛 ----
    by_size = {}
    for path, size in files:
        by_size.setdefault(size, []).append(path)

    if args.no_size_filter:
        candidates = [p for p, _ in files]
        print("已关闭大小预筛，将对全部文件计算 MD5...")
    else:
        candidates = [p for paths in by_size.values() if len(paths) > 1 for p in paths]
        same_size_groups = sum(1 for v in by_size.values() if len(v) > 1)
        print(f"大小预筛 : 有 {same_size_groups} 组文件大小相同（候选），"
              f"需要计算 MD5 的 {len(candidates)}/{len(files)} 个，"
              f"已跳过 {len(files) - len(candidates)} 个")
        if not candidates:
            print("没有任何大小相同的文件，不存在重复。")
            return

    # ---- 第二步：多线程计算 MD5 ----
    print(f"计算 MD5 : {len(candidates)} 个文件，{args.threads} 线程...")
    md5_map = {}
    with ThreadPoolExecutor(max_workers=args.threads) as pool:
        futures = {pool.submit(md5_of_file, p): p for p in candidates}
        done = 0
        for fut in as_completed(futures):
            path = futures[fut]
            try:
                md5_map.setdefault(fut.result(), []).append(path)
            except Exception as e:
                print(f"[错误] 读取失败: {path} ({e})")
            done += 1
            if done % 100 == 0 or done == len(candidates):
                print(f"        进度 {done}/{len(candidates)}")

    # ---- 第三步：汇总重复组 ----
    groups = sorted(
        ((md5, paths) for md5, paths in md5_map.items() if len(paths) > 1),
        key=lambda t: -os.path.getsize(t[1][0]),
    )
    dup_files = sum(len(paths) for _, paths in groups)
    saved_bytes = sum((len(paths) - 1) * os.path.getsize(paths[0]) for _, paths in groups)

    # ---- 第四步：输出 ----
    out_path = args.out or f"{Path(roots[0]).name or 'music'}_duplicates_report.txt"
    lines = [
        "=" * 70,
        "音乐文件 MD5 查重报告",
        "=" * 70,
        f"生成时间   : ",
        f"扫描目录   : {', '.join(roots)}",
        f"音频文件数 : {len(files)}",
        f"重复组数   : {len(groups)}",
        f"重复文件数 : {dup_files}",
        f"可节省空间 : {human_size(saved_bytes)}",
        "-" * 70,
    ]
    if not groups:
        lines.append("没有发现重复的音乐文件。")
    else:
        for i, (md5, paths) in enumerate(groups, 1):
            size = os.path.getsize(paths[0])
            lines.append(f"重复组 {i:>3}：{len(paths)} 个文件，每个 {human_size(size)}")
            lines.append(f"      MD5: {md5}")
            for p in paths:
                lines.append(f"        - {p}")
    lines.append("=" * 70)
    lines.append(f"提示：每组保留第 1 个文件即可，其余为内容相同的冗余文件。")

    report = "\n".join(lines) + "\n"
    print(report)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(report)
    print(f"报告已保存: {os.path.abspath(out_path)}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n已取消。")
        sys.exit(1)
