# StreamBadge

Something I threw together quickly since the http://streambadge.com/ Twitch Image generation was broken.

## How to use:
Visit https://twitch.kieve.dev/?user=TWITCH_USER
Replace TWITCH_USER with the appropriate username.

## Building
Using Maven.

After cloning the source, run:
```
mvn install
```

Build:
```
mvn compile package
```

Run:
```
java -jar target/StreamBadge-1.0.war
```
