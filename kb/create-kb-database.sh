#! /bin/bash

if [ "$1" == "" ]; then
  echo "Usage: ./create-kb-database.sh database.db"
  exit 1
fi

SCRIPT_DIR=$(dirname $0)

sqlite3 $1 < $SCRIPT_DIR/create-kb-database.sql
