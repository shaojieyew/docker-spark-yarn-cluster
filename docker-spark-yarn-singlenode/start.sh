docker build -t ysj/hadoop:1.0 .
docker run -it -p 9000:50070 -p 8088:8088 -p 8080:8080 -p 4040:4040 ysj/hadoop:1.0