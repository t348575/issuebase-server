
if [ "$1" == "-t" ]
then
    docker-compose -f docker-compose.yaml up -d
else
    docker-compose -f docker-compose-standard.yaml up -d
fi

if grep -q microsoft /proc/version; then
  name=issuebase-server-db-1
else
  name=issuebase-server_db_1
fi

sleep 5s

mysql_ip=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $name)

connection_string="mysql -h $mysql_ip -u issuebase -pissuebase issuebase < /up.sql"

docker exec -it "$(docker container ls  | grep $name | awk '{print $1}')" sh -c "$connection_string"

echo $name "$mysql_ip"

echo issuebase-server-redis-1 "$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $name)"

if [ "$1" == "-t" ]
then
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "mv webapps webapps2 && mv webapps.dist/ webapps"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "rm webapps/manager/META-INF/context.xml && cp context.xml webapps/manager/META-INF/context.xml"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "cd /issuebase-server/oauth && mvn tomcat7:deploy"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "cd /issuebase-server/projects && mvn tomcat7:deploy"
    docker exec -it "$(docker container ls | grep 'issuebase-server_tomcat_1' | awk '{print $1}')" sh -c "cd /issuebase-server/issues && mvn tomcat7:deploy"
fi