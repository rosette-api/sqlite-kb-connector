# Rosette Entity Extractor Sqlite Knowledge Base Connector

The Rosette Entity Extractor supports linking to custom knowledge bases instead of or in addition to the
default Wikidata knowledge base shipped with the product.
This package includes a sample knowledge base with an entry of `Boston`.
The knowledge base is stored as a sqlite database.

Requirements:
- Rosette Enterprise 1.17.0
- Rosette Enterprise license for Entity Extraction and English
- [OPTIONAL] If you start Rosette Server via docker-compose, Docker must be installed and running on your machine

## Build

Define some variables

```
export ROSAPI_HOME=/path-to-rosette-install/server directory
export KB_CONNECTOR_HOME=/path-to-this-directory
```
To build the Sqlite Knowledge Base Connector, run

```
cd $KB_CONNECTOR_HOME
mvn -Drosapi.home=$ROSAPI_HOME -P extract-flinx-api-jar
mvn -Drosapi.home=$ROSAPI_HOME -P build-kb-connector
```

This will build the `sqlite-kb-connector-1.0.jar` in the target directory.

## Integrate with Rosette Server

Copy `sqlite-kb-connector-1.0.jar` and `kb/` into Rosette Server
```
cp target/sqlite-kb-connector-1.0.jar $ROSAPI_HOME/launcher/bundles
cp -r kb $ROSAPI_HOME
```

Edit `$ROSAPI_HOME/launcher/config/rosapi/rex-factory-config.yaml` and add the following lines

```
#The option to link mentions to knowledge base entities with disambiguation model.
#Enabling this option also enables calculateConfidence.
linkEntities: true

#Custom list of Knowledge Bases for the linker, in order of priority.
kbs:
    - ../kb/MyKnowledgeBase1
    - ${rex-root}/data/flinx/data/kb/basis
```

## Run

Start Rosette Server
```
$ROSAPI_HOME/bin/launch.sh console
```
Call the /entities endpoint with linking to the custom knowledge base
```
curl -s --request POST 'http://localhost:8181/rest/v1/entities' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{"content":"Boston is beautiful.", "options": {"linkEntities": true}}' \
| jq .
``` 

The output is
```
{
    "entities": [
        {
            "type": "LOCATION",
            "mention": "Boston",
            "normalized": "Boston",
            "count": 1,
            "mentionOffsets": [
                {
                    "startOffset": 0,
                    "endOffset": 6
                }
            ],
            "entityId": "E2",
            "linkingConfidence": 0.74136907
        }
    ]
}
```
Notice that the entityId for `Boston` is E2, which comes from the knowledge base.

When linkEntities option is false
```
curl -s --request POST 'http://localhost:8181/rest/v1/entities' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{"content":"Boston is beautiful.", "options": {"linkEntities": false}}' \
| jq .
``` 
the entityId is T0, which is a temporary id.
```
{
    "entities": [
        {
            "type": "LOCATION",
            "mention": "Boston",
            "normalized": "Boston",
            "count": 1,
            "mentionOffsets": [
                {
                    "startOffset": 0,
                    "endOffset": 6
                }
            ],
            "entityId": "T0"
        }
    ]
}
```

## The Docker way

1. Edit the `docker-compose.yaml` file, adding the following files to the volumes section.
```
volumes:
  - rosette-roots-vol:/rosette/server/roots:ro
  - ${ROSAPI_LICENSE_PATH}:/rosette/server/launcher/config/rosapi/rosette-license.xml:ro
  - ${KB_CONNECTOR_HOME}/config/rex-factory-config.yaml:/rosette/server/launcher/config/rosapi/rex-factory-config.yaml
  - ${KB_CONNECTOR_HOME}/kb:/customKBs
  - ${KB_CONNECTOR_HOME}/target/sqlite-kb-connector-1.0.jar:/rosette/server/launcher/bundles/sqlite-kb-connector-1.0.jar:ro
```

2. Start the Rosette Server Docker container
```
ROSAPI_LICENSE_PATH=<path-to-license>/rosette-license.xml docker-compose up
```

Call the /entities endpoint with linking to the custom knowledge base
The result should be the same as show above.
