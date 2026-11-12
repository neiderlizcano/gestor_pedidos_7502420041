# Etapa 1: compilación
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp -P'!db-integration' dependency:go-offline

COPY src ./src
# Sin arranque de Spring, conexiones JDBC ni pruebas opcionales de integracion.
RUN mvn -B -ntp -P'!db-integration' clean package -DskipTests

# Etapa 2: ejecución
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Render permite leer /etc/secrets al grupo 1000; conservar ejecucion no root.
RUN groupadd --system app \
    && useradd --system --gid app app \
    && (getent group 1000 > /dev/null || groupadd --gid 1000 render-secrets) \
    && usermod -a -G 1000 app

COPY --from=build /workspace/target/gestorpedidos-*.jar /app/app.jar

# Puerto local orientativo; Spring usa PORT de Render o su fallback 8080.
EXPOSE 8080

USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
