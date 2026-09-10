# Stage 1: Build ứng dụng
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper và pom.xml để cache dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Cấp quyền thực thi cho mvnw và tải trước toàn bộ dependencies + plugins
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy mã nguồn
COPY src ./src

# Build file jar và bỏ qua tests
RUN ./mvnw -B clean package -Dmaven.test.skip=true
# Stage 2: Runtime nhẹ gọn
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo group và user không có quyền root để tăng tính bảo mật
RUN addgroup -S spring && adduser -S spring -G spring

# Copy file jar được tạo ra từ stage build
COPY --from=build /workspace/target/*.jar app.jar

# Chuyển sang user thường
USER spring:spring

EXPOSE 8080

# Cấu hình biến môi trường mặc định (có thể ghi đè khi docker run/ECS Task)
ENV JAVA_OPTS="-Xms128m -Xmx256m -Djava.net.preferIPv4Stack=true"

# Khởi chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]