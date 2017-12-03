package org.jboss.aerogear.unifiedpush.cassandra.dao.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.document.QueryOptions;
import org.jboss.aerogear.unifiedpush.cassandra.dao.AliasDao;
import org.jboss.aerogear.unifiedpush.cassandra.dao.DatabaseDao;
import org.jboss.aerogear.unifiedpush.cassandra.dao.DocumentDao;
import org.jboss.aerogear.unifiedpush.cassandra.dao.model.Database;
import org.jboss.aerogear.unifiedpush.cassandra.dao.model.DatabaseQueryKey;
import org.jboss.aerogear.unifiedpush.cassandra.dao.model.DocumentContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.repository.support.CassandraRepositoryFactory;
import org.springframework.stereotype.Repository;

import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.utils.UUIDs;

@Repository
public class NoSQLDocumentDaoImpl extends CassandraBaseDao<DocumentContent, DocumentKey>
		implements DocumentDao<DocumentContent, DocumentKey> {

	@Autowired
	private DatabaseDao databaseDao;
	@Autowired
	private AliasDao aliasDao;

	public NoSQLDocumentDaoImpl(@Autowired CassandraOperations operations) {
		super(DocumentContent.class, new CassandraRepositoryFactory(operations).getEntityInformation(DocumentContent.class),
				operations);
	}

	@Override
	public DocumentContent create(DocumentContent document) {
		// If snapshot exists request is to update a specific version.
		if (document.getKey().getSnapshot() != null)
			operations.update(document);
		else {
			// Populate unique snapshot version.
			document.getKey().snapshot = UUIDs.timeBased();

			insert((DocumentContent) document);

			// Populate database object if doesn't exists.
			DatabaseQueryKey qkey = new DatabaseQueryKey(document);
			if (databaseDao.findOne(qkey) == null)
				databaseDao.create(new Database(qkey));
		}

		return document;
	}

	@Override
	public Optional<DocumentContent> findOne(DocumentKey key) {
		return Optional.ofNullable(findOne(key, null));
	}

	@Override
	public DocumentContent findOne(DocumentKey key, String documentId) {
		if (key.getUserId() == null) {
			throw new UnsupportedOperationException("Unable to find single document for unknown alias.");
		}

		// Find document with specific version.
		if (key.getSnapshot() != null && documentId == null) {
			return super.findById(key).orElse(null);

		} else
			return findLatest(key, documentId);
	}

	/**
	 * Return newest document according to DocumentKey.
	 */
	private DocumentContent findLatest(DocumentKey queryKey, String documentId) {
		Select select = QueryBuilder.select().from(super.tableName);
		select.where(QueryBuilder.eq("push_application_id", queryKey.getPushApplicationId()));
		select.where(QueryBuilder.eq("database", queryKey.getDatabase()));
		select.where(QueryBuilder.eq("user_id", queryKey.getUserId()));

		// Also search by document logical id.
		if (documentId != null) {
			select.where(QueryBuilder.eq("document_id", documentId));
		}

		select.limit(1);

		return operations.selectOne(select, this.domainClass);
	}

	@Override
	public List<DocumentContent> findLatestForAliases(DocumentKey key, List<Alias> aliases, String logicalId) {
		List<DocumentContent> docs = new ArrayList<>();
		if (aliases == null)
			return docs;

		aliases.forEach((alias) -> {
			key.setUserId(alias.getId());
			// Query global documents for each database
			DocumentContent doc = findOne(key, logicalId);

			if (doc != null) {
				docs.add(doc);
			}
		});

		return docs;
	}

	private Stream<DocumentContent> find(DocumentKey queryKey) {
		return find(queryKey, null);
	}

	public Stream<DocumentContent> find(DocumentKey queryKey, QueryOptions options) {
		Select select = QueryBuilder.select().from(super.tableName);
		select.where(QueryBuilder.eq("push_application_id", queryKey.getPushApplicationId()));
		select.where(QueryBuilder.eq("database", queryKey.getDatabase()));
		select.where(QueryBuilder.eq("user_id", queryKey.getUserId()));

		if (options != null) {
			if (options.getFromDate() != null) {
				final UUID min = UUIDs.startOf(options.getFromDate());
				select.where(QueryBuilder.gte("snapshot", min));
			}
			if (options.getToDate() != null) {
				final UUID max = UUIDs.endOf(options.getToDate());
				select.where(QueryBuilder.lt("snapshot", max));
			}

			// Also search by document logical id.
			if (StringUtils.isNoneEmpty(options.getId())) {
				select.where(QueryBuilder.eq("document_id", options.getId()));
			}

			if (options.getLimit() != null && options.getLimit().intValue() > 0)
				select.limit(options.getLimit());
		}

		return operations.stream(select, domainClass);
	}

	@Override
	public void delete(UUID pushApplicaitonId) {
		// First query an delete all DB related documents.
		databaseDao.find(pushApplicaitonId).forEach((db) -> {

			// Query global documents for each database
			find(new DocumentKey(db)).forEach((doc) -> {
				delete(doc);
			});

			delete(pushApplicaitonId, db.getDatabase());

			// Delete database
			databaseDao.delete(db);
		});
	}

	@Override
	public void delete(UUID pushApplicaitonId, Alias alias) {
		// First query an delete all DB related documents.
		databaseDao.find(pushApplicaitonId).forEach((db) -> {
			deleteById(new DocumentKey(pushApplicaitonId, db.getDatabase(), alias.getId()));
		});
	}

	/*
	 * Delete application/alias documents from all 12 partitions (by month).
	 *
	 * For a planet scale database, we can also create MV by day (365
	 * partitions).
	 */
	private void delete(UUID pushApplicationId, String database) {
		aliasDao.findUserIds(pushApplicationId).forEach(row -> {
			deleteById(new DocumentKey(pushApplicationId, database, row.getUUID(0)));
		});
	}

	@Override
	public void deleteById(DocumentKey key) {
		// Delete all documents by partition key
		if (key.getSnapshot() == null) {
			Delete delete = QueryBuilder.delete().from(super.tableName);
			delete.where(QueryBuilder.eq("push_application_id", key.getPushApplicationId()));
			delete.where(QueryBuilder.eq("database", key.getDatabase()));
			delete.where(QueryBuilder.eq("user_id", key.getUserId()));

			operations.getCqlOperations().execute(delete);
		} else {
			super.deleteById(key);
		}
	}
}
