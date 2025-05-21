## 課題 3 実行方法

### コンパイル

※Work4/server-app のディレクトリで実行する。

```bash
mvn clean compile
```

### 実行

#### Server.java の実行

```bash
mvn exec:java -Dexec.mainClass="work4.Server"
```

#### Client.java の実行(例)

```bash
mvn exec:java -Dexec.mainClass="work4.Client" -Dexec.args="-count 8 2"
```

## 課題 4 実行手順

### 1. client-app の起動

```bash
cd /mnt/c/Users/vgnri/workspace/Univ/realtime-data/work4/client-app
npm run dev
```

リンクをクリックしてブラウザを開く。

### 2. コンパイル

```bash
mvn clean compile
```

### 3. Server.java の実行

```bash
mvn exec:java -Dexec.mainClass="work4.Server"
```

### 4. Client.java の実行(例)

```bash
mvn exec:java -Dexec.mainClass="work4.Client" -Dexec.args="-count 8 2"
```

### 5. ブラウザのリロード
