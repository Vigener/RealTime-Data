## 実行方法

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
