FROM mcr.microsoft.com/devcontainers/base:ubuntu

# システムパッケージの更新
RUN apt-get update && apt-get install -y \
    curl \
    git \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Apache Flink のインストール（オプション：ローカル実行用）
ENV FLINK_VERSION=1.18.0
ENV FLINK_HOME=/opt/flink
RUN wget -q "https://archive.apache.org/dist/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_2.12.tgz" \
    && tar -xzf "flink-${FLINK_VERSION}-bin-scala_2.12.tgz" \
    && mv "flink-${FLINK_VERSION}" ${FLINK_HOME} \
    && rm "flink-${FLINK_VERSION}-bin-scala_2.12.tgz" \
    && chown -R vscode:vscode ${FLINK_HOME}

# パスの設定
ENV PATH="${FLINK_HOME}/bin:${PATH}"

# 作業ディレクトリの設定
WORKDIR /workspace

# vscodeユーザーに権限を付与
RUN chown -R vscode:vscode /workspace