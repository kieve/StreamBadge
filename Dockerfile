FROM azul/zulu-openjdk:12

# Ports and Volumes
VOLUME /config

# Install
COPY target/StreamBadge-1.0.war /run.war

# Run
ENTRYPOINT ["java", "-jar", "run.war", "--docker"]
