#!bin/bash
pkill -f ZvServer
cd target
java -cp "zenvisage/WEB-INF/lib/*:zenvisage-jar-with-dependencies.jar" edu.uiuc.zenvisage.server.ZvServer

