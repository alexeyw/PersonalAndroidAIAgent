package ai.agent.android.data.tools.local

import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBytesTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionOneOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.appfunctions.metadata.AppFunctionUnitTypeMetadata
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Unit tests for [AppFunctionDataCodec].
 *
 * The codec splits its work into pure helpers ([AppFunctionDataCodec.planWriteOps],
 * [AppFunctionDataCodec.renderError], [AppFunctionDataCodec.renderSuccess]) and a thin
 * builder-application path. The pure helpers are exercised here on the plain JVM — they
 * never touch `AppFunctionData.Builder`, whose `<clinit>` reaches into Android stubs and
 * cannot run in unit tests. The builder-application path is covered by the Phase 20-6
 * end-to-end instrumented test.
 */
class AppFunctionDataCodecTest {

    private lateinit var codec: AppFunctionDataCodec
    private val warnLogs = mutableListOf<String>()
    private val warnTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.WARN) warnLogs.add(message)
        }
    }

    @Before
    fun setUp() {
        codec = AppFunctionDataCodec()
        Timber.plant(warnTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(warnTree)
        warnLogs.clear()
    }

    @Test
    fun `planWriteOps emits SetString for a string parameter`() {
        val schema = listOf(stringParam("title", required = true))

        val ops = codec.planWriteOps("""{"title":"hello"}""", schema)

        assertEquals(listOf(AppFunctionWriteOp.SetString("title", "hello")), ops)
    }

    @Test
    fun `planWriteOps emits the matching scalar op for int long double float and boolean`() {
        val schema = listOf(
            param("answer", AppFunctionIntTypeMetadata(isNullable = false)),
            param("ts", AppFunctionLongTypeMetadata(isNullable = false)),
            param("ratio", AppFunctionDoubleTypeMetadata(isNullable = false)),
            param("temperature", AppFunctionFloatTypeMetadata(isNullable = false)),
            param("enabled", AppFunctionBooleanTypeMetadata(isNullable = false)),
        )
        val json = """{"answer":42,"ts":1700000000000,"ratio":0.5,"temperature":36.6,"enabled":true}"""

        val ops = codec.planWriteOps(json, schema)

        assertEquals(
            listOf<AppFunctionWriteOp>(
                AppFunctionWriteOp.SetInt("answer", 42),
                AppFunctionWriteOp.SetLong("ts", 1_700_000_000_000L),
                AppFunctionWriteOp.SetDouble("ratio", 0.5),
                AppFunctionWriteOp.SetFloat("temperature", 36.6f),
                AppFunctionWriteOp.SetBoolean("enabled", true),
            ),
            ops,
        )
    }

    @Test
    fun `planWriteOps emits SetStringList for an array of strings`() {
        val schema = listOf(
            param(
                "tags",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionStringTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"tags":["a","b","c"]}""", schema)

        assertEquals(listOf(AppFunctionWriteOp.SetStringList("tags", listOf("a", "b", "c"))), ops)
    }

    @Test
    fun `planWriteOps emits SetIntArray for an array of ints`() {
        val schema = listOf(
            param(
                "values",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionIntTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"values":[1,2,3]}""", schema)

        assertEquals(listOf(AppFunctionWriteOp.SetIntArray("values", intArrayOf(1, 2, 3))), ops)
    }

    @Test
    fun `planWriteOps emits SetLongArray for an array of longs`() {
        val schema = listOf(
            param(
                "ids",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionLongTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"ids":[10,11,12]}""", schema)

        assertEquals(listOf(AppFunctionWriteOp.SetLongArray("ids", longArrayOf(10, 11, 12))), ops)
    }

    @Test
    fun `planWriteOps emits SetDoubleArray for an array of doubles`() {
        val schema = listOf(
            param(
                "ratios",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionDoubleTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"ratios":[0.5,0.25]}""", schema)

        assertEquals(
            listOf(AppFunctionWriteOp.SetDoubleArray("ratios", doubleArrayOf(0.5, 0.25))),
            ops,
        )
    }

    @Test
    fun `planWriteOps emits SetFloatArray for an array of floats`() {
        val schema = listOf(
            param(
                "temps",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionFloatTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"temps":[36.6,37.0]}""", schema)

        assertEquals(
            listOf(AppFunctionWriteOp.SetFloatArray("temps", floatArrayOf(36.6f, 37.0f))),
            ops,
        )
    }

    @Test
    fun `planWriteOps emits SetBooleanArray for an array of booleans`() {
        val schema = listOf(
            param(
                "flags",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionBooleanTypeMetadata(isNullable = false),
                    isNullable = false,
                ),
            ),
        )

        val ops = codec.planWriteOps("""{"flags":[true,false,true]}""", schema)

        assertEquals(
            listOf(AppFunctionWriteOp.SetBooleanArray("flags", booleanArrayOf(true, false, true))),
            ops,
        )
    }

    @Test
    fun `planWriteOps recursively plans nested objects via SetObject`() {
        val nestedType = AppFunctionObjectTypeMetadata(
            properties = mapOf("street" to AppFunctionStringTypeMetadata(isNullable = false)),
            required = listOf("street"),
            qualifiedName = "com.example.Address",
            isNullable = false,
        )
        val schema = listOf(param("address", nestedType))

        val ops = codec.planWriteOps("""{"address":{"street":"Main"}}""", schema)

        val expectedChildren = listOf(AppFunctionWriteOp.SetString("street", "Main"))
        assertEquals(
            listOf<AppFunctionWriteOp>(
                AppFunctionWriteOp.SetObject("address", nestedType, expectedChildren),
            ),
            ops,
        )
    }

    @Test
    fun `planWriteOps emits SetObjectList for arrays of nested objects`() {
        val nestedType = AppFunctionObjectTypeMetadata(
            properties = mapOf("name" to AppFunctionStringTypeMetadata(isNullable = false)),
            required = listOf("name"),
            qualifiedName = "com.example.Item",
            isNullable = false,
        )
        val schema = listOf(
            param(
                "items",
                AppFunctionArrayTypeMetadata(itemType = nestedType, isNullable = false),
            ),
        )

        val ops = codec.planWriteOps("""{"items":[{"name":"x"},{"name":"y"}]}""", schema)

        val expectedItems = listOf(
            listOf(AppFunctionWriteOp.SetString("name", "x")),
            listOf(AppFunctionWriteOp.SetString("name", "y")),
        )
        assertEquals(
            listOf<AppFunctionWriteOp>(
                AppFunctionWriteOp.SetObjectList("items", nestedType, expectedItems),
            ),
            ops,
        )
    }

    @Test
    fun `planWriteOps silently skips a missing optional field`() {
        val schema = listOf(stringParam("note", required = false))

        val ops = codec.planWriteOps("""{}""", schema)

        assertTrue(ops.isEmpty())
    }

    @Test
    fun `planWriteOps treats explicit JSON null as a missing optional value`() {
        val schema = listOf(
            stringParam("note", required = false),
            param("score", AppFunctionIntTypeMetadata(isNullable = true)),
        )

        val ops = codec.planWriteOps("""{"note":null,"score":null}""", schema)

        assertTrue(ops.isEmpty())
    }

    @Test
    fun `planWriteOps throws IllegalArgumentException naming the missing required field`() {
        val schema = listOf(stringParam("title", required = true))

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.planWriteOps("""{}""", schema)
        }
        assertTrue(error.message!!.contains("'title'"))
    }

    @Test
    fun `planWriteOps drops an extra JSON field and logs a warning that names the field`() {
        val schema = listOf(stringParam("title", required = true))

        val ops = codec.planWriteOps("""{"title":"hi","ghost":1}""", schema)

        assertEquals(listOf(AppFunctionWriteOp.SetString("title", "hi")), ops)
        assertTrue(warnLogs.any { it.contains("'ghost'") })
    }

    @Test
    fun `planWriteOps surfaces JSONException for malformed input`() {
        val schema = listOf(stringParam("title", required = true))

        assertThrows(JSONException::class.java) {
            codec.planWriteOps("""{title":}""", schema)
        }
    }

    @Test
    fun `planWriteOps rejects reference types with field name and reference id in the message`() {
        val schema = listOf(
            param(
                "profile",
                AppFunctionReferenceTypeMetadata(
                    referenceDataType = "components/Profile",
                    isNullable = false,
                ),
            ),
        )

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"profile":{"id":1}}""", schema)
        }
        assertTrue(error.message!!.contains("'profile'"))
        assertTrue(error.message!!.contains("components/Profile"))
    }

    @Test
    fun `planWriteOps rejects allOf types`() {
        val schema = listOf(
            param(
                "composed",
                AppFunctionAllOfTypeMetadata(
                    matchAll = emptyList(),
                    qualifiedName = null,
                    isNullable = false,
                ),
            ),
        )

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"composed":{}}""", schema)
        }
        assertTrue(error.message!!.contains("'composed'"))
    }

    @Test
    fun `planWriteOps rejects oneOf types`() {
        val schema = listOf(
            param(
                "animal",
                AppFunctionOneOfTypeMetadata(
                    matchOneOf = emptyList(),
                    qualifiedName = "com.example.Animal",
                    isNullable = false,
                ),
            ),
        )

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"animal":{}}""", schema)
        }
        assertTrue(error.message!!.contains("'animal'"))
    }

    @Test
    fun `planWriteOps rejects parcelable types`() {
        val schema = listOf(
            param(
                "intent",
                AppFunctionParcelableTypeMetadata(
                    qualifiedName = "android.app.PendingIntent",
                    isNullable = false,
                ),
            ),
        )

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"intent":{}}""", schema)
        }
        assertTrue(error.message!!.contains("'intent'"))
    }

    @Test
    fun `planWriteOps rejects bytes types`() {
        val schema = listOf(param("blob", AppFunctionBytesTypeMetadata(isNullable = false)))

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"blob":"abc"}""", schema)
        }
        assertTrue(error.message!!.contains("'blob'"))
    }

    @Test
    fun `planWriteOps rejects unit types`() {
        val schema = listOf(param("nothing", AppFunctionUnitTypeMetadata()))

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"nothing":{}}""", schema)
        }
        assertTrue(error.message!!.contains("'nothing'"))
    }

    @Test
    fun `planWriteOps rejects arrays whose item type is itself an array`() {
        val schema = listOf(
            param(
                "matrix",
                AppFunctionArrayTypeMetadata(
                    itemType = AppFunctionArrayTypeMetadata(
                        itemType = AppFunctionIntTypeMetadata(isNullable = false),
                        isNullable = false,
                    ),
                    isNullable = false,
                ),
            ),
        )

        val error = assertThrows(UnsupportedAppFunctionTypeException::class.java) {
            codec.planWriteOps("""{"matrix":[[1,2]]}""", schema)
        }
        assertTrue(error.message!!.contains("'matrix'"))
    }

    @Test
    fun `planWriteOps fails when a required nested-object field is missing`() {
        val nestedType = AppFunctionObjectTypeMetadata(
            properties = mapOf("street" to AppFunctionStringTypeMetadata(isNullable = false)),
            required = listOf("street"),
            qualifiedName = "com.example.Address",
            isNullable = false,
        )
        val schema = listOf(param("address", nestedType))

        val error = assertThrows(IllegalArgumentException::class.java) {
            codec.planWriteOps("""{"address":{}}""", schema)
        }
        assertTrue(error.message!!.contains("'street'"))
    }

    @Test
    fun `renderError emits a JSON object with the supplied message`() {
        val json = codec.renderError("boom")

        assertEquals("boom", JSONObject(json).getString("error"))
    }

    @Test
    fun `renderError falls back to a generic message when input is null`() {
        val json = codec.renderError(null)

        assertTrue(JSONObject(json).getString("error").isNotBlank())
    }

    @Test
    fun `renderError falls back to a generic message when input is blank`() {
        val json = codec.renderError("   ")

        assertTrue(JSONObject(json).getString("error").isNotBlank())
    }

    @Test
    fun `renderSuccess emits the first non-null scalar in the string-long-double-boolean order`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { true },
            getString = { "ok" },
            getLong = { null },
            getDouble = { null },
            getBoolean = { null },
            key = "value",
        )

        assertEquals("ok", JSONObject(json).getString("result"))
    }

    @Test
    fun `renderSuccess falls through to long when string getter returns null`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { true },
            getString = { null },
            getLong = { 7L },
            getDouble = { null },
            getBoolean = { null },
            key = "value",
        )

        assertEquals(7L, JSONObject(json).getLong("result"))
    }

    @Test
    fun `renderSuccess falls through to double when string and long are null`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { true },
            getString = { null },
            getLong = { null },
            getDouble = { 1.5 },
            getBoolean = { null },
            key = "value",
        )

        assertEquals(1.5, JSONObject(json).getDouble("result"), 1e-9)
    }

    @Test
    fun `renderSuccess falls through to boolean when scalars before it are null`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { true },
            getString = { null },
            getLong = { null },
            getDouble = { null },
            getBoolean = { true },
            key = "value",
        )

        assertTrue(JSONObject(json).getBoolean("result"))
    }

    @Test
    fun `renderSuccess yields null result for empty qualified name`() {
        val json = codec.renderSuccess(
            qualifiedName = "",
            containsKey = { true },
            getString = { "ignored" },
            getLong = { 1L },
            getDouble = { 1.0 },
            getBoolean = { true },
            key = "value",
        )

        val parsed = JSONObject(json)
        assertTrue(parsed.has("result"))
        assertTrue(parsed.isNull("result"))
    }

    @Test
    fun `renderSuccess yields null result when the response does not contain the key`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { false },
            getString = { "ignored" },
            getLong = { 1L },
            getDouble = { 1.0 },
            getBoolean = { true },
            key = "value",
        )

        val parsed = JSONObject(json)
        assertTrue(parsed.has("result"))
        assertTrue(parsed.isNull("result"))
    }

    @Test
    fun `renderSuccess yields null result when every getter returns null`() {
        val json = codec.renderSuccess(
            qualifiedName = "com.example.Result",
            containsKey = { true },
            getString = { null },
            getLong = { null },
            getDouble = { null },
            getBoolean = { null },
            key = "value",
        )

        val parsed = JSONObject(json)
        assertTrue(parsed.has("result"))
        assertTrue(parsed.isNull("result"))
    }

    @Test
    fun `planWriteOps preserves declaration order`() {
        val schema = listOf(
            param("b", AppFunctionIntTypeMetadata(isNullable = false)),
            param("a", AppFunctionStringTypeMetadata(isNullable = false)),
            param("c", AppFunctionBooleanTypeMetadata(isNullable = false)),
        )

        val ops = codec.planWriteOps("""{"a":"x","b":1,"c":true}""", schema)

        assertEquals(listOf("b", "a", "c"), ops.map { it.key })
    }

    @Test
    fun `planWriteOps drops every extra JSON field with a warning`() {
        val schema = listOf(stringParam("title", required = true))

        val ops = codec.planWriteOps("""{"title":"hi","extra1":1,"extra2":"y"}""", schema)

        assertEquals(1, ops.size)
        assertTrue(warnLogs.any { it.contains("'extra1'") })
        assertTrue(warnLogs.any { it.contains("'extra2'") })
        assertFalse(warnLogs.any { it.contains("'title'") })
    }

    @Test
    fun `renderError returns a parseable JSON document`() {
        val parsed = JSONObject(codec.renderError("oops"))

        assertNotNull(parsed)
    }

    private fun param(name: String, dataType: AppFunctionDataTypeMetadata) =
        AppFunctionParameterMetadata(name = name, isRequired = false, dataType = dataType)

    private fun stringParam(name: String, required: Boolean) = AppFunctionParameterMetadata(
        name = name,
        isRequired = required,
        dataType = AppFunctionStringTypeMetadata(isNullable = false),
    )
}
