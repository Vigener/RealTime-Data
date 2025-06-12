現在のディレクトリ構成を分析して、改善提案をします。

## 現在の問題点と改善アドバイス

### 1. **命名の一貫性の問題**

**現状**: `Work1`, `Work2`, `Work3`, `Work4`, `Work5`, `Work5-1`, `work6`
- 大文字小文字が混在 (`Work` vs `work`)
- 区切り文字が混在 (`Work5-1` vs その他)

**推奨**: **小文字 + ハイフン区切り**
```
work-01/  work-02/  work-03/  work-04/  work-05/  work-05-1/  work-06/
```

**理由**:
- **Linux/Unix標準**: 小文字が一般的
- **可読性**: ハイフンは単語の区切りが明確
- **タブ補完**: 小文字は入力が速い
- **ゼロパディング**: `01`, `02` でソート順が自然

### 2. **サブディレクトリの命名**

**現状**: `client-app`, `server-app`
**推奨**: **kebab-case (ハイフン区切り)** を継続

**理由**:
- **Web開発標準**: npm パッケージ名と同じ規則
- **URL安全**: そのままURLに使用可能
- **可読性**: アンダースコアより読みやすい

### 3. **ファイル名の命名**

**現状**: `stock_data.txt`
**推奨**: **snake_case (アンダースコア)** を継続

**理由**:
- **伝統的**: Unix/Linuxでファイル名はアンダースコアが一般的
- **プログラム変数**: 多くの言語で変数名がsnake_case
- **区別**: ディレクトリはkebab-case、ファイルはsnake_caseで区別

### 4. **推奨ディレクトリ構成**

```
realtime-data/
├── assignments/           # 課題フォルダを分離
│   ├── work-01/
│   ├── work-02/
│   ├── work-03/
│   ├── work-04/
│   ├── work-05/
│   ├── work-05-1/         # サブバリエーション
│   └── work-06/
├── shared/                # 共通リソース
│   ├── data/
│   │   └── stock_data.txt
│   ├── utils/
│   └── configs/
├── archive/               # 古いバージョン
├── docs/                  # ドキュメント
│   ├── base.md
│   └── memo.txt
├── tools/                 # 開発ツール
└── workspace.code-workspace
```

### 5. **具体的な改善提案**

#### A. **課題フォルダの統一**
```bash
# 現在の構造を整理
mkdir -p assignments/{work-01,work-02,work-03,work-04,work-05,work-05-1,work-06}
mv Work1 assignments/work-01
mv Work2 assignments/work-02
# ... 以下同様
```

#### B. **共通リソースの分離**
```bash
mkdir -p shared/{data,utils,configs}
mv stock_data.txt shared/data/
mv package.json shared/configs/
```

#### C. **各課題の標準構造**
```
work-05-1/
├── client-app/           # フロントエンド
├── server-app/           # バックエンド
├── README.md             # 課題説明
├── requirements.txt      # 依存関係
└── docs/                 # 課題固有ドキュメント
```

### 6. **命名規則のベストプラクティス**

| 要素 | 規則 | 例 | 理由 |
|------|------|-----|------|
| プロジェクトフォルダ | kebab-case | `realtime-data` | URL安全、可読性 |
| 課題フォルダ | kebab-case + ゼロパディング | `work-01` | ソート順、一貫性 |
| アプリフォルダ | kebab-case | `client-app` | Web標準 |
| ファイル名 | snake_case | `stock_data.txt` | Unix伝統 |
| 設定ファイル | kebab-case | package.json | 慣習 |

### 7. **追加の改善提案**

#### A. **バージョン管理の改善**
```bash
# Git管理の改善
echo "node_modules/" >> .gitignore
echo "target/" >> .gitignore
echo "*.log" >> .gitignore
```

#### B. **ドキュメント構造**
```
docs/
├── assignment-01.md
├── assignment-02.md
├── setup-guide.md
└── troubleshooting.md
```

#### C. **スクリプト管理**
```
scripts/
├── setup.sh             # 環境セットアップ
├── build-all.sh          # 全プロジェクトビルド
└── clean.sh              # クリーンアップ
```

### 8. **移行スクリプト例**

```bash
#!/bin/bash
# migrate.sh - 現在の構造を推奨構造に移行

mkdir -p assignments shared/{data,utils,configs} docs tools

# 課題フォルダの移行
for i in {1..6}; do
    if [ -d "Work$i" ]; then
        mv "Work$i" "assignments/work-$(printf "%02d" $i)"
    fi
done

# Work5-1の特別処理
if [ -d "Work5-1" ]; then
    mv "Work5-1" "assignments/work-05-1"
fi

# 共通ファイルの移行
mv stock_data.txt shared/data/ 2>/dev/null || true
mv *.md docs/ 2>/dev/null || true

echo "Migration completed!"
```

この構造により、**スケーラビリティ**、**保守性**、**可読性**が大幅に向上し、将来的な課題追加や管理が容易になります。

Gitの履歴を保持しながらディレクトリ構造を変更する手順を示します。

## 推奨手順: 段階的リファクタリング

### Phase 1: 命名の統一 (履歴を保持)

```bash
# 1. 現在の状態をコミット
git add -A
git commit -m "Save current state before restructuring"
git push origin main

# 2. git mvで履歴を保持しながらリネーム
git mv Work1 work-01
git mv Work2 work-02
git mv Work3 work-03
git mv Work4 work-04
git mv Work5 work-05
git mv Work5-1 work-05-1
git mv work6 work-06  # 既存のwork6も統一

# 3. リネーム結果をコミット
git add -A
git commit -m "Rename assignments: Work* -> work-* with zero padding"
git push origin main
```

### Phase 2: ディレクトリ構造の整理

```bash
# 4. 新しいディレクトリ構造を作成
mkdir -p assignments shared/{data,utils,configs} docs tools

# 5. git mvで履歴を保持しながら移動
git mv work-01 assignments/
git mv work-02 assignments/
git mv work-03 assignments/
git mv work-04 assignments/
git mv work-05 assignments/
git mv work-05-1 assignments/
git mv work-06 assignments/

# 6. 共通ファイルの移動
git mv stock_data.txt shared/data/
git mv base.md docs/
git mv memo.txt docs/

# package.json等がある場合
git mv package.json shared/configs/
git mv package-lock.json shared/configs/

# 7. 構造変更をコミット
git add -A
git commit -m "Restructure: move assignments to dedicated folder and organize shared resources"
git push origin main
```

### Phase 3: 不要ファイルのクリーンアップ

```bash
# 8. 不要なファイル/フォルダを削除
git rm -r Archive  # もし不要であれば
git rm -r Copy     # もし不要であれば

# 9. .gitignoreの更新
cat >> .gitignore << EOF
# Build artifacts
target/
node_modules/
dist/

# IDE files
.vscode/settings.json
.idea/

# OS files
.DS_Store
Thumbs.db

# Log files
*.log
EOF

# 10. クリーンアップ結果をコミット
git add -A
git commit -m "Clean up unnecessary files and update .gitignore"
git push origin main
```

### Phase 4: ドキュメント整備

```bash
# 11. READMEとドキュメントを追加
cat > README.md << 'EOF'
# リアルタイムデータ処理と分析

大学の授業課題のリポジトリです。

## ディレクトリ構造

```
realtime-data/
├── assignments/     # 課題フォルダ
│   ├── work-01/    # 課題1
│   ├── work-02/    # 課題2
│   └── ...
├── shared/         # 共通リソース
├── docs/          # ドキュメント
└── tools/         # 開発ツール
```

## 課題一覧

- work-01: [課題1の説明]
- work-02: [課題2の説明]
- ...
EOF

# 12. 各課題にREADMEを追加
for i in {01..06}; do
    if [ -d "assignments/work-$i" ]; then
        cat > "assignments/work-$i/README.md" << EOF
# Work $i

## 概要
[課題$iの説明]

## 実行方法
[実行手順]

## 成果物
[成果物の説明]
EOF
    fi
done

# 13. ドキュメント追加をコミット
git add -A
git commit -m "Add comprehensive documentation and README files"
git push origin main
```

## 履歴確認コマンド

```bash
# ファイルの移動履歴を確認
git log --follow --stat assignments/work-01/
git log --follow shared/data/stock_data.txt

# 全体の変更履歴を確認
git log --oneline --graph
```

## 代替案: ブランチを使用した安全な移行

より慎重にやりたい場合：

```bash
# 1. リファクタリング用ブランチを作成
git checkout -b restructure-directories
git push -u origin restructure-directories

# 2. 上記のPhase 1-4を実行

# 3. Pull Requestを作成してレビュー
# (GitHubでPull Request作成)

# 4. 問題なければメインブランチにマージ
git checkout main
git merge restructure-directories
git push origin main

# 5. 作業ブランチを削除
git branch -d restructure-directories
git push origin --delete restructure-directories
```

## 最終的な構造確認

```bash
# 最終構造の確認
tree realtime-data/ -I 'node_modules|target'
```

期待される出力:
```
realtime-data/
├── assignments/
│   ├── work-01/
│   ├── work-02/
│   ├── work-03/
│   ├── work-04/
│   ├── work-05/
│   ├── work-05-1/
│   └── work-06/
├── shared/
│   ├── data/
│   │   └── stock_data.txt
│   ├── utils/
│   └── configs/
├── docs/
│   ├── base.md
│   └── memo.txt
├── tools/
├── README.md
└── .gitignore
```

## 利点

1. **履歴保持**: `git mv`により全ての変更履歴が保持される
2. **段階的変更**: 各フェーズでコミットすることで、問題があれば特定の段階に戻れる
3. **レビュー可能**: ブランチを使えばPull Requestでレビューできる
4. **バックアップ**: 各段階でpushすることで、リモートにバックアップされる

この手順により、履歴を完全に保持しながら、推奨されるディレクトリ構造に移行できます。