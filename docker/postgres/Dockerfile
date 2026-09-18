# 汗牛充栋 PostgreSQL 镜像：官方 postgres:16（debian bookworm）+ zhparser 中文分词扩展
#
# 编译安装：
#   - SCWS 1.2.3（https://github.com/hightman/scws，LGPL-2.1，动态链接）
#   - zhparser v2.3（https://github.com/amutu/zhparser，PostgreSQL License）
#
# 后端启动时会自动 CREATE EXTENSION zhparser 并创建 chinese_zh 全文检索配置；
# 若使用不含 zhparser 的镜像，后端会自动降级为 LIKE + 拼音搜索。
FROM postgres:16

ARG SCWS_VERSION=1.2.3
ARG ZHPARSER_VERSION=v2.3

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        postgresql-server-dev-16 \
        ca-certificates \
        curl \
        xz-utils \
    # 编译安装 SCWS
    && mkdir -p /tmp/scws-src \
    && curl -fsSL "https://github.com/hightman/scws/archive/refs/tags/${SCWS_VERSION}.tar.gz" \
        | tar -xz -C /tmp/scws-src --strip-components=1 \
    && cd /tmp/scws-src \
    && ./configure \
    && make -j"$(nproc)" \
    && make install-strip \
    # 编译安装 zhparser（SCWS 安装在 /usr/local，通过 SCWS_PATH 显式指定）
    && mkdir -p /tmp/zhparser-src \
    && curl -fsSL "https://github.com/amutu/zhparser/archive/refs/tags/${ZHPARSER_VERSION}.tar.gz" \
        | tar -xz -C /tmp/zhparser-src --strip-components=1 \
    && cd /tmp/zhparser-src \
    && make -j"$(nproc)" SCWS_PATH=/usr/local \
    && make install \
    && ldconfig \
    # 清理编译工具链，保持镜像精简
    && apt-get purge -y build-essential postgresql-server-dev-16 curl xz-utils \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
