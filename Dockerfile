# syntax=docker/dockerfile:1
# 多阶段构建：容器内 mvn package（触发 frontend-maven-plugin 编译前端）+ jre-alpine 运行
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# CI 构建时注入 GitHub Packages 认证，用于拉取私有依赖 com.kK-2004:kk-common
# （该包位于另一个仓库 kK-2004/kk-common，GITHUB_TOKEN 无权读取，必须用 PAT）
# 本地构建若已配置 ~/.m2/settings.xml 可留空
ARG GITHUB_ACTOR=
ARG GITHUB_TOKEN=
RUN if [ -n "$GITHUB_TOKEN" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
        '<settings>' \
        '  <servers>' \
        '    <server>' \
        '      <id>github</id>' \
        "      <username>${GITHUB_ACTOR}</username>" \
        "      <password>${GITHUB_TOKEN}</password>" \
        '    </server>' \
        '  </servers>' \
        '</settings>' > /root/.m2/settings.xml ; \
    fi

# 先拷依赖清单利用层缓存
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests || true

# 源码 + 前端（frontend-maven-plugin 会在 package 阶段编译前端到 src/main/resources/static/admin）
COPY src ./src
COPY frontend ./frontend
RUN mvn -B clean package -DskipTests

# 拷出可执行 jar（排除 sources jar）
RUN cp "$(ls -1 target/kmessage-*.jar | grep -vE 'sources|javadoc' | head -n 1)" /app.jar

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app app
WORKDIR /app
COPY --from=builder --chown=app:app /app.jar /app/app.jar
RUN mkdir -p /app/logs && chown -R app:app /app
USER app
EXPOSE 8002
VOLUME ["/app/logs"]
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
