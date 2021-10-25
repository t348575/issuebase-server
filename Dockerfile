FROM maven:3.8.3-openjdk-17 AS builder
COPY . /issuebase-server
WORKDIR /issuebase-server
RUN ./deploy/deploy

FROM tomcat:9.0.54-jdk17-openjdk-bullseye
RUN mkdir /issuebase-server
RUN apt update
RUN apt install maven -y
COPY --from=builder /issuebase-server /issuebase-server
EXPOSE 8080
CMD ["catalina.sh", "run"]