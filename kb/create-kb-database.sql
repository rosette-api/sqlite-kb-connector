CREATE TABLE IF NOT EXISTS "entityTypes" (
    "entityId" TEXT NOT NULL PRIMARY KEY UNIQUE,
    "entityType" TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS "relatedEntities" (
    "entityId1" TEXT NOT NULL,
    "entityId2" TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS "aliases" (
    "alias" TEXT NOT NULL,
    "entityId" TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS "contextWords" (
    "entityId" TEXT NOT NULL PRIMARY KEY UNIQUE,
    "contextWords" TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS "entities" (
	"entityNum"	INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE,
	"entityId"	TEXT
);

CREATE TABLE IF NOT EXISTS "canonicalNames" (
    "entityId" TEXT NOT NULL PRIMARY KEY UNIQUE,
    "canonicalName" TEXT NOT NULL
);

