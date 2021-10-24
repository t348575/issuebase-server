#!/bin/bash
docker-compose up -d

sleep 5s

mysql_ip=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' issuebase-server-db-1)

connection_string="mysql -h $mysql_ip -u issuebase -pissuebase issuebase < /up.sql"

docker exec -it "$(docker container ls  | grep 'issuebase-server-db-1' | awk '{print $1}')" sh -c "$connection_string"

echo issuebase-server_db_1 "$mysql_ip"

echo issuebase-server_redis_1 "$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' issuebase-server-db-1)"