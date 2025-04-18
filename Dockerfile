# 使用 Maven 构建镜像
FROM maven:3.8.6-openjdk-11-slim AS build
  
  # 设置工作目录
WORKDIR /app
  
  # 复制 pom.xml 和源代码
COPY pom.xml .
COPY src ./src
  
  # 构建项目
RUN mvn clean package -DskipTests
  
  # 使用 OpenJDK 运行应用程序
FROM maven:3.8.6-openjdk-11-slim
  
  # 设置工作目录
WORKDIR /app
  
  # 复制构建的 JAR 文件到新镜像
COPY --from=build /app/target/milvus-java-sdk-toos-1.0-jar-with-dependencies.jar app.jar
  
  # 指定容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar"]