#!/bin/bash
docker-compose up -d

sleep 5s

mysql_ip=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' issuebase-server_db_1)

connection_string="mysql -h $mysql_ip -u open_exam -popen_exam open_exam < /up.sql"

docker exec -it "$(docker container ls  | grep 'issuebase-server_db_1' | awk '{print $1}')" sh -c "$connection_string"

echo issuebase-server_db_1 " " "$mysql_ip"