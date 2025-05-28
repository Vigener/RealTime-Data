1. flink と mavne, java のインストール
   ```bash
   # flinkのインストール
   brew install apache-flink
   # mavenのインストール
   brew install maven
   # javaの場所を調べ，JAVA_HOMEを設定する．javaがまだインストールされていない場合は先に以下を行う．
   ```
   もし java がインストールできていないならばそれもインストールする．
   ```bash
   # javaの確認
   java -version
   # brewでインストール可能なjavaを調べる
   brew search openjdk
   # 必要なバージョンをインストール
   # 例えば
   brew install openjdk@11
   ```
2. 以下のコマンドでも用意できる．
   ```bash
   # groupIdやartifactIdは自分で決める．
   mvn archetype:generate -DgroupId=fuga -DartifactId=hoge -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
   ```
   このコマンドを使用すると，artifactId で指定したディレクトリをルートとする以下のディレクトリ構成が生成されるので，適切な位置に java や txt のファイルを配置し，必要に応じてファイル中のパスなどを編集する．
   ```bash
   (base) user@user hoge % tree
   .
   ├── pom.xml
   └── src
       ├── main
       │   └── java
       │       └── fuga
       │           └── App.java
       └── test
           └── java
               └── fuga
                   └── AppTest.java
   ```
3. pom.xml の用意  
   flink や akka について[mvn repository](https://mvnrepository.com/)で依存関係を調べ，追加する．

4. パッケージをインストールして jar ファイルを用意し，実行

   ```bash
   # パッケージインストール
   mvn clean install
   # SocketWriterの実行
   java -cp /path/to/jar com.example.flink.SocketWriter
   # 別のタブを開き，SocketStreamReadingを実行
   java -cp /path/to/jar com.example.flink.SocketStreamReading

   # もしくは，
   ./run.sh
   ```
