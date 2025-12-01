#!/usr/bin/env python3
"""
Mermaid 다이어그램 추출 및 PNG 변환 스크립트
"""

import os
import re
import subprocess
import sys

# 대상 파일들
TARGET_FILES = [
    ("ARCHITECTURE.md", "architecture"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/NPC/README.md", "npc"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/FleaMarket/README.md", "fleamarket"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/Database/README.md", "database"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/BookSystem/README.md", "booksystem"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/Economy/README.md", "economy"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/AdvancedLandClaiming/README.md", "advancedland"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/VillageMerchant/README.md", "villagemerchant"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/Command/README.md", "command"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/MyLand/README.md", "myland"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/Items/README.md", "items"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/FishMerchant/README.md", "fishmerchant"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/Roulette/README.md", "roulette"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/FarmVillage/README.md", "farmvillage"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/ChatSystem/README.md", "chatsystem"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/PlayTime/README.md", "playtime"),
    ("src/main/kotlin/com/lukehemmin/lukeVanilla/System/MultiServer/README.md", "multiserver"),
]

OUTPUT_DIR = "docs/images/diagrams"

def extract_mermaid_blocks(content):
    """마크다운에서 mermaid 코드 블록 추출"""
    pattern = r'```mermaid\n(.*?)```'
    return re.findall(pattern, content, re.DOTALL)

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_converted = 0
    
    for filepath, prefix in TARGET_FILES:
        if not os.path.exists(filepath):
            print(f"파일 없음: {filepath}")
            continue
            
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        blocks = extract_mermaid_blocks(content)
        
        if not blocks:
            print(f"다이어그램 없음: {filepath}")
            continue
        
        print(f"처리 중: {filepath} ({len(blocks)}개 다이어그램)")
        
        for i, block in enumerate(blocks, 1):
            # 다이어그램 유형 판단
            if 'sequenceDiagram' in block:
                dtype = 'flow'
            elif 'classDiagram' in block:
                dtype = 'class'
            else:
                dtype = 'diagram'
            
            suffix = f"-{i}" if len(blocks) > 1 else ""
            filename = f"{prefix}-{dtype}{suffix}"
            mmd_path = os.path.join(OUTPUT_DIR, f"{filename}.mmd")
            png_path = os.path.join(OUTPUT_DIR, f"{filename}.png")
            
            # .mmd 파일 저장
            with open(mmd_path, 'w', encoding='utf-8') as f:
                f.write(block.strip())
            
            # mmdc로 PNG 변환
            try:
                result = subprocess.run(
                    ['mmdc', '-i', mmd_path, '-o', png_path, '-b', 'transparent', '--scale', '2'],
                    capture_output=True,
                    text=True,
                    timeout=60
                )
                if result.returncode == 0:
                    print(f"  변환 완료: {filename}.png")
                    total_converted += 1
                else:
                    print(f"  변환 실패: {filename} - {result.stderr}")
            except subprocess.TimeoutExpired:
                print(f"  시간 초과: {filename}")
            except Exception as e:
                print(f"  오류: {filename} - {e}")
    
    print(f"\n총 {total_converted}개 다이어그램 변환 완료")

if __name__ == "__main__":
    main()