/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.test.integration.functions.codegen;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;
import org.apache.sysml.api.DMLScript;
import org.apache.sysml.api.DMLScript.RUNTIME_PLATFORM;
import org.apache.sysml.hops.OptimizerUtils;
import org.apache.sysml.lops.LopProperties.ExecType;
import org.apache.sysml.runtime.matrix.data.MatrixValue.CellIndex;
import org.apache.sysml.test.integration.AutomatedTestBase;
import org.apache.sysml.test.integration.TestConfiguration;
import org.apache.sysml.test.utils.TestUtils;

public class SumProductChainTest extends AutomatedTestBase 
{	
	private static final String TEST_NAME1 = "SumProductChain";
	private static final String TEST_NAME2 = "SumAdditionChain";
	private static final String TEST_DIR = "functions/codegen/";
	private static final String TEST_CLASS_DIR = TEST_DIR + SumProductChainTest.class.getSimpleName() + "/";
	private final static String TEST_CONF = "SystemML-config-codegen.xml";
	
	private static final int rows = 1191;
	private static final int cols1 = 1;
	private static final int cols2 = 31;
	private static final double sparsity1 = 0.9;
	private static final double sparsity2 = 0.09;
	private static final double eps = Math.pow(10, -10);
	
	@Override
	public void setUp() {
		TestUtils.clearAssertionInformation();
		addTestConfiguration( TEST_NAME1, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME1, new String[] { "R" }) );
		addTestConfiguration( TEST_NAME2, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME2, new String[] { "R" }) );
	}
		
	@Test
	public void testSumProductVectorsDense() {
		testSumProductChain( TEST_NAME1, true, false, false, ExecType.CP );
	}
	
	@Test
	public void testSumProductVectorsSparse() {
		testSumProductChain( TEST_NAME1, true, true, false, ExecType.CP );
	}
	
	@Test
	public void testSumProductMatrixDense() {
		testSumProductChain( TEST_NAME1, false, false, false, ExecType.CP );
	}
	
	@Test
	public void testSumProductMatrixSparse() {
		testSumProductChain( TEST_NAME1, false, true, false, ExecType.CP );
	}
	
	@Test
	public void testSumAdditionVectorsDense() {
		testSumProductChain( TEST_NAME2, true, false, false, ExecType.CP );
	}
	
	@Test
	public void testSumAdditionVectorsSparse() {
		testSumProductChain( TEST_NAME2, true, true, false, ExecType.CP );
	}
	
	@Test
	public void testSumAdditionMatrixDense() {
		testSumProductChain( TEST_NAME2, false, false, false, ExecType.CP );
	}
	
	@Test
	public void testSumAdditionMatrixSparse() {
		testSumProductChain( TEST_NAME2, false, true, false, ExecType.CP );
	}
	
	
	private void testSumProductChain(String testname, boolean vectors, boolean sparse, boolean rewrites, ExecType instType)
	{	
		RUNTIME_PLATFORM oldPlatform = rtplatform;
		boolean oldRewrites = OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION;
		
		switch( instType ){
			case MR: rtplatform = RUNTIME_PLATFORM.HADOOP; break;
			case SPARK: rtplatform = RUNTIME_PLATFORM.SPARK; break;
			default: rtplatform = RUNTIME_PLATFORM.HYBRID; break;
		}
		boolean oldSparkConfig = DMLScript.USE_LOCAL_SPARK_CONFIG;
		if( rtplatform == RUNTIME_PLATFORM.SPARK || rtplatform == RUNTIME_PLATFORM.HYBRID_SPARK )
			DMLScript.USE_LOCAL_SPARK_CONFIG = true;
		
		try
		{
			OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION = rewrites;
			
			TestConfiguration config = getTestConfiguration(testname);
			loadTestConfiguration(config);
			
			String HOME = SCRIPT_DIR + TEST_DIR;
			fullDMLScriptName = HOME + testname + ".dml";
			programArgs = new String[]{"-explain", "-stats", 
					"-config=" + HOME + TEST_CONF, "-args", input("X"), output("R") };
			
			fullRScriptName = HOME + testname + ".R";
			rCmd = getRCmd(inputDir(), expectedDir());			

			//generate input data
			int cols = vectors ? cols1 : cols2;
			double sparsity = sparse ? sparsity2 : sparsity1;
			double[][] X = getRandomMatrix(rows, cols, -1, 1, sparsity, 7);
			writeInputMatrixWithMTD("X", X, true);
			
			//run tests
			runTest(true, false, null, -1); 
			runRScript(true); 
			
			//compare matrices 
			HashMap<CellIndex, Double> dmlfile = readDMLMatrixFromHDFS("R");
			HashMap<CellIndex, Double> rfile  = readRMatrixFromFS("R");	
			TestUtils.compareMatrices(dmlfile, rfile, eps, "Stat-DML", "Stat-R");
			if( vectors || !sparse  )
				Assert.assertTrue(heavyHittersContainsSubString("spoof") 
						|| heavyHittersContainsSubString("sp_spoof"));
		}
		finally {
			rtplatform = oldPlatform;
			DMLScript.USE_LOCAL_SPARK_CONFIG = oldSparkConfig;
			OptimizerUtils.ALLOW_ALGEBRAIC_SIMPLIFICATION = oldRewrites;
			OptimizerUtils.ALLOW_AUTO_VECTORIZATION = true;
			OptimizerUtils.ALLOW_OPERATOR_FUSION = true;
		}
	}	
}