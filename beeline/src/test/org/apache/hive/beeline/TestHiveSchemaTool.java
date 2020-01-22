package org.apache.hive.beeline;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.tools.HiveSchemaHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({ HiveSchemaHelper.class, HiveSchemaTool.CommandBuilder.class })
public class TestHiveSchemaTool {

  String scriptFile = System.getProperty("java.io.tmpdir") + File.separator + "someScript.sql";
  @Mock
  private HiveConf hiveConf;
  private HiveSchemaTool.CommandBuilder builder;
  private String pasword = "reallySimplePassword";

  @Before
  public void setup() throws IOException {
    mockStatic(HiveSchemaHelper.class);
    when(HiveSchemaHelper
        .getValidConfVar(eq(HiveConf.ConfVars.METASTORECONNECTURLKEY), same(hiveConf)))
        .thenReturn("someURL");
    when(HiveSchemaHelper
        .getValidConfVar(eq(HiveConf.ConfVars.METASTORE_CONNECTION_DRIVER), same(hiveConf)))
        .thenReturn("someDriver");

    File file = new File(scriptFile);
    if (!file.exists()) {
      file.createNewFile();
    }
    builder = new HiveSchemaTool.CommandBuilder(hiveConf, "testUser", pasword, scriptFile);
  }

  @After
  public void globalAssert() throws IOException {
    verifyStatic();
    HiveSchemaHelper.getValidConfVar(eq(HiveConf.ConfVars.METASTORECONNECTURLKEY), same(hiveConf));
    HiveSchemaHelper
        .getValidConfVar(eq(HiveConf.ConfVars.METASTORE_CONNECTION_DRIVER), same(hiveConf));

    new File(scriptFile).delete();
  }

  @Test
  public void shouldReturnStrippedPassword() throws IOException {
    assertFalse(builder.buildToLog().contains(pasword));
  }

  @Test
  public void shouldReturnActualPassword() throws IOException {
    String[] strings = builder.buildToRun();
    assertTrue(Arrays.asList(strings).contains(pasword));
  }
}