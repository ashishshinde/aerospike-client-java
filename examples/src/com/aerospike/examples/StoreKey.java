/*
 * Copyright 2012-2021 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.examples;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.Statement;
import com.aerospike.client.task.IndexTask;

public class StoreKey extends Example {

	public StoreKey(Console console) {
		super(console);
	}

	/**
	 * Store user key on server using WritePolicy.sendKey option.
	 */
	@Override
	public void runExample(AerospikeClient client, Parameters params) throws Exception {
		String indexName = "skindex";
		String keyPrefix = "skkey";
		String binName = params.getBinName("skbin");
		int size = 10;

		createIndex(client, params, indexName, binName);
		writeRecords(client, params, keyPrefix, binName, size);
		runQuery(client, params, indexName, binName);
		client.dropIndex(params.policy, params.namespace, params.set, indexName);
	}

	private void createIndex(
		AerospikeClient client,
		Parameters params,
		String indexName,
		String binName
	) throws Exception {
		console.info("Create index: ns=%s set=%s index=%s bin=%s",
			params.namespace, params.set, indexName, binName);

		Policy policy = new Policy();
		policy.socketTimeout = 0; // Do not timeout on index create.

		try {
			IndexTask task = client.createIndex(policy, params.namespace, params.set, indexName, binName, IndexType.NUMERIC);
			task.waitTillComplete();
		}
		catch (AerospikeException ae) {
			if (ae.getResultCode() != ResultCode.INDEX_ALREADY_EXISTS) {
				throw ae;
			}
		}
	}

	private void writeRecords(
		AerospikeClient client,
		Parameters params,
		String keyPrefix,
		String binName,
		int size
	) throws Exception {
		console.info("Write " + size + " records with store user key option.");
		WritePolicy policy = new WritePolicy();
		policy.sendKey = true;

		for (int i = 1; i <= size; i++) {
			Key key = new Key(params.namespace, params.set, keyPrefix + i);
			Bin bin = new Bin(binName, i);
			client.put(policy, key, bin);
		}
	}

	private void runQuery(
		AerospikeClient client,
		Parameters params,
		String indexName,
		String binName
	) throws Exception {

		int begin = 2;
		int end = 5;

		console.info("Query user key for: ns=%s set=%s index=%s bin=%s >= %s <= %s",
			params.namespace, params.set, indexName, binName, begin, end);

		Statement stmt = new Statement();
		stmt.setNamespace(params.namespace);
		stmt.setSetName(params.set);
		stmt.setBinNames(binName);
		stmt.setFilter(Filter.range(binName, begin, end));

		RecordSet rs = client.query(null, stmt);

		try {
			int count = 0;

			while (rs.next()) {
				Key key = rs.getKey();

				if (key.userKey != null) {
					Object userkey = key.userKey.getObject();
					Record record = rs.getRecord();
					int result = record.getInt(binName);

					if (userkey != null) {
						console.info("Record found with user key: ns=%s set=%s bin=%s userkey=%s value=%s",
							key.namespace, key.setName, binName, userkey, result);
					}
					else {
						console.error("Record found with null user key: ns=%s set=%s bin=%s userkey=null value=%s",
							key.namespace, key.setName, binName, result);
					}
				}
				else {
					console.error("Record found with null user key: ns=%s set=%s bin=%s userkey=null",
						key.namespace, key.setName, binName);
				}
				count++;
			}

			if (count != 4) {
				console.error("Query count mismatch. Expected 4. Received " + count);
			}
		}
		finally {
			rs.close();
		}
	}
}
