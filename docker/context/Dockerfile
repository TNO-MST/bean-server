FROM maven:3.9-eclipse-temurin-21-alpine AS build

ARG VERSION="0.0.0-git"

COPY . .

RUN sed -i  "s/0.0.0-git/$VERSION/" ./src/main/java/nl/tno/beanserver/BeanServer.java
RUN sed -i  "s/UNKNOWN_BUILD_DATE/$(date +"%Y-%m-%d %T")/" ./src/main/java/nl/tno/beanserver/BeanServer.java

RUN mvn -B versions:set -DnewVersion="$VERSION" -DprocessAllModules=true
RUN mvn -B clean package

# Entire JDK needed for --add-exports
FROM eclipse-temurin:21-jdk-alpine
ARG VERSION="0.0.0-git"
RUN addgroup -S nonroot && adduser -S nonroot -G nonroot
COPY --chown=nonroot:nonroot ./docker/context/application /home/nonroot
COPY --from=build --chown=nonroot:nonroot target/beanserver-$VERSION-shaded.jar /home/nonroot/beanserver-shaded.jar
USER nonroot
WORKDIR /home/nonroot

CMD ["java", "--add-exports",  "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",  "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports",  "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",  "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED", "--add-exports",  "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",  "-jar", "beanserver-shaded.jar"]

