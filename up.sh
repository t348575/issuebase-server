#!/bin/bash
docker-compose up -d

sleep 5s

mysql_ip=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' issuebase-server-db-1)

connection_string="mysql -h $mysql_ip -u issuebase -pissuebase issuebase < /up.sql"

docker exec -it "$(docker container ls  | grep 'issuebase-server-db-1' | awk '{print $1}')" sh -c "$connection_string"

echo issuebase-server-db-1 "$mysql_ip"

echo issuebase-server-redis-1 "$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' issuebase-server-db-1)"

if [ "$1" == "-t" ]
then
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "mv webapps webapps2 && mv webapps.dist/ webapps"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "rm webapps/manager/META-INF/context.xml && cp context.xml webapps/manager/META-INF/context.xml"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "cd /issuebase-server/oauth && mvn tomcat7:deploy"
fi