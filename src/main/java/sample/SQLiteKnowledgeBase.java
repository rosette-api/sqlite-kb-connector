/******************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp.  It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2020 Basis Technology Corporation All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ******************************************************************************/

package sample;

import com.basistech.rosette.RosetteRuntimeException;
import com.basistech.rosette.dm.Token;
import com.basistech.rosette.flinx.api.BaseCandidateEntity;
import com.basistech.rosette.flinx.api.BaseCandidateMention;
import com.basistech.rosette.flinx.api.ContextVectorHandler;
import com.basistech.rosette.flinx.api.KnowledgeBase;
import com.basistech.rosette.flinx.api.data.Document;
import com.basistech.rosette.flinx.api.data.ScoredValue;
import com.basistech.rosette.flinx.api.service.KnowledgeBaseVariantFactory;
import com.basistech.util.LanguageCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is a sample Custom Knowledge Base Connector that retrieves linker data from
 * a SQLite database.
 * The schema for the database required for this connector, as well as a short script
 * to create an empty database, can be found under the kb folder in this sample.
 */

public final class SQLiteKnowledgeBase implements KnowledgeBase {
    public static final int DEFAULT_MAX_ENTITY_TOKEN_LEN = 5;
    public static final String KB_VARIANT_ID = "SQLITE";
    public static final String KB_NAME = "SQLite";
    public static final String DB_NAME = "custom-kb.db";

    private static final Logger LOG = LoggerFactory.getLogger(SQLiteKnowledgeBase.class);

    private final LanguageCode language;
    private final String url;
    private final Connection connection;
    private final int maxEntityTokenLen;


    // Connectors are passed a reference to the Knowledge Base's folder and the requested language.
    // Knowledge Bases may contain information for multiple languages, but connector instances
    // will be created for each language separately.

    public SQLiteKnowledgeBase(Path kbDir, LanguageCode language) {
        // Force registration of JDBC driver via static initializer (needed for OSGi)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RosetteRuntimeException(e);
        }

        // Read connector configuration
        Path confFile = kbDir.resolve("sqlite-conf.json");
        ObjectMapper mapper = new ObjectMapper();
        if (Files.exists(confFile)) {
            try (BufferedReader reader = Files.newBufferedReader(confFile, Charsets.UTF_8)) {
                SQLiteKnowledgeBaseConfiguration config = mapper.readValue(reader, SQLiteKnowledgeBaseConfiguration.class);
                maxEntityTokenLen = config.getMaxEntityTokenLen();
            } catch (IOException e) {
                LOG.error(e.getMessage());
                throw new RosetteRuntimeException(e);
            }
        } else {
            maxEntityTokenLen = DEFAULT_MAX_ENTITY_TOKEN_LEN;
        }

        // Inititlize JDBC with URL to database
        this.url = String.format("jdbc:sqlite:%s", kbDir.resolve(DB_NAME).toAbsolutePath());
        String[] fields = url.split(File.separator);

        this.language = language;

        Connection c = null;

        try {
            c = DriverManager.getConnection(url);
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
        this.connection = c;
    }

    public List<String> lookupAlias(String alias) {
        List<String> ids = Lists.newArrayList();
        String sql = "SELECT * FROM aliases WHERE alias LIKE ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, alias);
            ResultSet rs = pstmt.executeQuery();

            // loop through the result set
            while (rs.next()) {
                ids.add(rs.getString("entityId"));
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return ids;
    }

    private String getTokenString(Document document, int startToken, int num) {
        int startOffset = document.getStartOffset(startToken);
        int endOffset = document.getEndOffset(startToken + (num - 1));
        String docText = document.getText();
        if (endOffset > docText.length()) {
            return docText.substring(startOffset);
        }

        return document.getText().substring(startOffset, endOffset);
    }

    // Generates a list of strings starting at token startToken up to maxEntityTokenLen.
    // For example, for the document "Donald Trump is the president of the United States" for
    // the default maxEntityTokenLen of 5 and starting at token 0, it will generate
    // "Donald"
    // "Donald Trump"
    // "Donald Trump is"
    // "Donald Trump is the"
    // "Donald Trump is the president"

    private String[] getAliasTokens(Document document, int startToken) {
        int arrayLen = Math.min(maxEntityTokenLen, document.getTokens().size() - startToken);
        String[] ret = new String[arrayLen];
        for (int i = 0; i < arrayLen; i++) {
            ret[i] = getTokenString(document, startToken, arrayLen - i);
        }
        return ret;
    }

    private Map<String, List<String>> lookupAlias(String[] aliasTokens) {
        // Looks up any aliases that are equal to any of the strings in aliasTokens

        Map<String, List<String>> ids = Maps.newHashMap();

        String[] sqlTokens = new String[aliasTokens.length];
        for (int i = 0; i < aliasTokens.length; i++) {
            sqlTokens[i] = "(?)";
        }
        String inSection = "(" + String.join(", ", sqlTokens) + ")";

        String sql = "SELECT * FROM aliases WHERE alias IN " + inSection +  " ORDER BY LENGTH(alias) DESC";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            for (int i = 1; i <= aliasTokens.length; i++) {
                pstmt.setString(i, aliasTokens[i - 1]);
            }

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String alias = rs.getString("alias");
                if (ids.containsKey(alias)) {
                    ids.get(alias).add(rs.getString("entityId"));
                } else {
                    List<String> idList = Lists.newArrayList();
                    idList.add(rs.getString("entityId"));
                    ids.put(alias, idList);
                }
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return ids;
    }

    // The Knowledge Base Variant Name is the identifier for this particular connector.
    // The linker identifies the correct connector to use by matching the string returned
    // from this function to the contetnst of a file kb.variant inside the kb folder.

    @Override
    public String getKnowledgeBaseVariantName() {
        return KB_VARIANT_ID;
    }

    // The Knowledge Base Name will be returned as the subsource in entities extracted
    // by this connector. This name can be overridden by supplying a file kb.name in
    // the kb folder.

    @Override
    public String getKnowledgeBaseName() {
        return KB_NAME;
    }

    // findCandidates should return every possible candidate mention in the supplied Document.
    // A candidate mention (BaseCandidateMention) is composed of the tokens in the original document
    // that the mention is comprised of (first and last token indexes), and a list of possible
    // entities (id, type) the mention may refer to. The list should always include a nil candidate
    // (BaseCandidateEntity.nilCandidate()).

    @Override
    public List<BaseCandidateMention> findCandidates(Document document, boolean training) {
        LOG.info("Generating candidates with SQLite backend");

        List<BaseCandidateMention> ret = Lists.newArrayList();
        List<Token> docTokens = document.getTokens();
        for (int i = 0; i < docTokens.size(); i++) {
            String[] aliasTokens = getAliasTokens(document, i);
            Map<String, List<String>> aliases = lookupAlias(aliasTokens);
            if (aliases.size() > 0) {
                for (String alias : aliases.keySet()) {
                    List<BaseCandidateEntity> candidates = Lists.newArrayList(BaseCandidateEntity.nilCandidate());

                    List<String> ids = aliases.get(alias);
                    for (String id: ids) {
                        candidates.add(new BaseCandidateEntity(id, lookupEntityType(id), getExtendedProperties(id)));
                    }
                    // Find out how many tokens in the entity
                    int numTokens = 0;
                    for (int j = 1; j <= aliasTokens.length; j++) {
                        if (alias.equals(getTokenString(document, i, j))) {
                            numTokens = j;
                            break;
                        }
                    }

                    ret.add(new BaseCandidateMention(i, i + numTokens, candidates, language));
                }
            }
        }
        return ret;
    }

    // getContextVector() uses the supplied ContextVectorHandler to return a context vector
    // for a specific entity that might be extracted by the connector.
    // The vector will be compared to actual document context to disambiguate candidates.

    @Override
    public float[] getContextVector(String entityId, ContextVectorHandler cvh) {
        String words = null;
        String sql = "SELECT * FROM contextWords WHERE entityId = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, entityId);
            ResultSet rs = pstmt.executeQuery();


            if (rs.next()) {
                words = rs.getString("contextWords");
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        if (words != null && words.length() > 0) {
            return cvh.computeContextVector(Arrays.asList(words.split(" ")));
        }

        return null;

    }

    // getEntityType() returns the type of entities that might be extracted by the
    // connector. Types may be any string, but it's recommended to use standard
    // REX types like PERSON or LOCATION

    @Override
    public String lookupEntityType(String entityId) {
        String sql = "SELECT * FROM entityTypes WHERE entityId = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, entityId);
            ResultSet rs = pstmt.executeQuery();

            // Attempt to move to the first row in rs - returns based on success
            if (rs.next()) {
                return rs.getString("entityType");
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // isImportantLink() returns true if the two entities supplied are strongly
    // related to one another. It is used in the IMPORTANT_LINK disambiguation
    // model feature.

    @Override
    public boolean isImportantLink(String entityId1, String entityId2) {
        String sql = "SELECT * FROM relatedEntities WHERE (entityId1 = ? AND entityId2 = ?)";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, entityId1);
            pstmt.setString(2, entityId2);
            ResultSet rs = pstmt.executeQuery();
            boolean response = rs.next();
            rs.close();
            return response;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    // getLabels() returns a list of possible labels (names) for entities
    // that might be extravted by the connector.

    @Override
    public List<String> getLabels(String entityId) {
        return lookupAlias(entityId);
    }

    // getCanonicalTitle() returns a canonical title (name) associated with
    // an entity. The linker might use this value in the Normalized Title field
    // of the extracted entity. For example, you can use this to return "United States of America"
    // as an entity title even if the document's text refers to it as "US".

    @Override
    public String getCanonicalTitle(String entityId) {
        String sql = "SELECT * FROM canonicalNames WHERE entityId = ?";
        try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
            pstmt.setString(1, entityId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("canonicalName");
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    // Connectors can return a variety of information about the entities they support,
    // but most is optional and can be implemented by returning null or empty values,
    // as seen below. Implement only the functions required by the features used
    // in the disambiguation models of your custom knowledge base.

    @Override
    public List<ScoredValue<String>> getCrosswikiProbabilities(String mention) {
        return Lists.newArrayList();
    }

    @Override
    public Double getLinkProbability(String mention) {
        return null;
    }

    @Override
    public Double getNormalizedLinkProbability(String mention) {
        return null;
    }

    @Override
    public Double getCapitalizationProbability(String mention) {
        return null;
    }

    @Override
    public int getHitCount(String entityId) {
        return 0;
    }

    @Override
    public int getMaxRank() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getRank(String entityId) {
        return 0;
    }

    @Override
    public int getSitelinkscount(String entityId) {
        return 0;
    }

    @Override
    public int getMaxSitelinksCount() {
        return 361;
    }

    @Override
    public Map<String, String> getExtendedProperties(String entityId) {
        return Collections.emptyMap();
    }


    @Override
    public List<String> getClaims(String entityId) {
        return null;
    }


    // Connectors must include this KnowledgeBaseVariantFactory implementation
    // to be findable by the linker.

    @AutoService(KnowledgeBaseVariantFactory.class)
    public static final class BuilderService implements KnowledgeBaseVariantFactory {
        @Override
        public String getVariantName() {
            return KB_VARIANT_ID;
        }

        @Override
        public KnowledgeBase build(Path kbDir, LanguageCode language) {
            return new SQLiteKnowledgeBase(kbDir, language);
        }
    }
}
