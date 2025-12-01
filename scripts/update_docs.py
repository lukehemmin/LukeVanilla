#!/usr/bin/env python3
"""
README 파일 업데이트 스크립트
Mermaid 코드 블록을 이미지 + details 태그로 변경
"""

import os
import re
import glob

# 대상 파일들과 이미지 경로 매핑
TARGET_FILES = [
    {
        "file": "ARCHITECTURE.md",
        "prefix": "architecture",
        "image_base": "docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/NPC/README.md",
        "prefix": "npc",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/FleaMarket/README.md",
        "prefix": "fleamarket",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/Database/README.md",
        "prefix": "database",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/BookSystem/README.md",
        "prefix": "booksystem",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/Economy/README.md",
        "prefix": "economy",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/AdvancedLandClaiming/README.md",
        "prefix": "advancedland",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/VillageMerchant/README.md",
        "prefix": "villagemerchant",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/Command/README.md",
        "prefix": "command",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/MyLand/README.md",
        "prefix": "myland",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/Items/README.md",
        "prefix": "items",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/FishMerchant/README.md",
        "prefix": "fishmerchant",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/Roulette/README.md",
        "prefix": "roulette",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/FarmVillage/README.md",
        "prefix": "farmvillage",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/ChatSystem/README.md",
        "prefix": "chatsystem",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/PlayTime/README.md",
        "prefix": "playtime",
        "image_base": "../../../../../../docs/images/diagrams"
    },
    {
        "file": "src/main/kotlin/com/lukehemmin/lukeVanilla/System/MultiServer/README.md",
        "prefix": "multiserver",
        "image_base": "../../../../../../docs/images/diagrams"
    },
]

def get_available_images(prefix):
    """prefix에 해당하는 이미지 파일 목록 반환"""
    pattern = f"docs/images/diagrams/{prefix}-*.png"
    files = sorted(glob.glob(pattern))
    return [os.path.basename(f) for f in files]

def get_diagram_desc(block):
    """다이어그램 설명 추출"""
    if 'sequenceDiagram' in block:
        return "시퀀스 다이어그램"
    elif 'classDiagram' in block:
        return "클래스 다이어그램"
    elif 'graph' in block or 'flowchart' in block:
        return "시스템 구조도"
    else:
        return "다이어그램"

def update_file(filepath, prefix, image_base):
    """파일 업데이트"""
    if not os.path.exists(filepath):
        print(f"파일 없음: {filepath}")
        return False
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # mermaid 블록 찾기
    pattern = r'```mermaid\n(.*?)```'
    matches = list(re.finditer(pattern, content, re.DOTALL))
    
    if not matches:
        print(f"다이어그램 없음: {filepath}")
        return False
    
    # 해당 prefix의 이미지 파일 목록 가져오기
    available_images = get_available_images(prefix)
    
    if not available_images:
        print(f"이미지 없음: {prefix}")
        return False
    
    print(f"처리 중: {filepath} ({len(matches)}개 다이어그램, {len(available_images)}개 이미지)")
    
    # 뒤에서부터 교체 (인덱스 유지를 위해)
    new_content = content
    for i, match in enumerate(reversed(matches), 1):
        real_index = len(matches) - i + 1
        block = match.group(1)
        
        # 이미지 파일명 결정 (순서대로 매칭)
        if real_index <= len(available_images):
            filename = available_images[real_index - 1]
        else:
            print(f"  경고: 다이어그램 {real_index}에 해당하는 이미지 없음")
            continue
        
        image_path = f"{image_base}/{filename}"
        desc = get_diagram_desc(block)
        
        # 교체 텍스트 생성
        replacement = f'''![{desc}]({image_path})

<details>
<summary>📊 다이어그램 소스 코드 (AI 참조용)</summary>

```mermaid
{block.strip()}
```

</details>'''
        
        new_content = new_content[:match.start()] + replacement + new_content[match.end():]
    
    # 파일 저장
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)
    
    print(f"  업데이트 완료: {filepath}")
    return True

def main():
    updated = 0
    for target in TARGET_FILES:
        if update_file(target["file"], target["prefix"], target["image_base"]):
            updated += 1
    
    print(f"\n총 {updated}개 파일 업데이트 완료")

if __name__ == "__main__":
    main()