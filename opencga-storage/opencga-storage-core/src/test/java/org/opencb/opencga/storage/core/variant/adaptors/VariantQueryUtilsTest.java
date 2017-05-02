/*
 * Copyright 2015-2016 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.core.variant.adaptors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.test.GenericTest;

import java.util.*;

import static org.junit.Assert.*;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam.ANNOTATION_EXISTS;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.*;

/**
 * Created on 01/02/16
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantQueryUtilsTest extends GenericTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCheckOperatorAND() throws Exception {
        assertEquals(VariantQueryUtils.QueryOperation.AND, VariantQueryUtils.checkOperator("a;b;c"));
    }

    @Test
    public void testCheckOperatorOR() throws Exception {
        assertEquals(VariantQueryUtils.QueryOperation.OR, VariantQueryUtils.checkOperator("a,b,c"));
    }

    @Test
    public void testCheckOperatorANY() throws Exception {
        assertNull(VariantQueryUtils.checkOperator("a"));
    }

    @Test
    public void testCheckOperatorMix() throws Exception {
        thrown.expect(VariantQueryException.class);
        VariantQueryUtils.checkOperator("a,b;c");
    }

    @Test
    public void testSplitOperator() throws Exception {
        assertArrayEquals(new String[]{"key", "=", "value"}, VariantQueryUtils.splitOperator("key=value"));
    }

    @Test
    public void testSplitOperatorTrim() throws Exception {
        assertArrayEquals(new String[]{"key", "=", "value"}, VariantQueryUtils.splitOperator("key = value"));
    }

    @Test
    public void testSplitOperatorMissingKey() throws Exception {
        assertArrayEquals(new String[]{"", "<", "value"}, VariantQueryUtils.splitOperator("<value"));
    }

    @Test
    public void testSplitOperatorNoOperator() throws Exception {
        assertArrayEquals(new String[]{null, "=", "value"}, VariantQueryUtils.splitOperator("value"));
    }

    @Test
    public void testSplitOperatorUnknownOperator() throws Exception {
        assertArrayEquals(new String[]{null, "=", ">>>value"}, VariantQueryUtils.splitOperator(">>>value"));
    }

    @Test
    public void testSplitOperators() throws Exception {
        test("=");
        test("==");
        test("!");
        test("!=");
        test("<");
        test("<=");
        test(">");
        test(">=");
        test("~");
        test("=~");
    }

    private void test(String operator) {
        test("key", operator, "value");
        test("", operator, "value");
    }

    private void test(String key, String operator, String value) {
        assertArrayEquals("Split " + key + operator + value, new String[]{key, operator, value}, VariantQueryUtils.splitOperator(key + operator + value));
    }

    @Test
    public void testIsValid() {
        assertFalse(isValidParam(new Query(), ANNOTATION_EXISTS));
        assertFalse(isValidParam(new Query(ANNOTATION_EXISTS.key(), null), ANNOTATION_EXISTS));
        assertFalse(isValidParam(new Query(ANNOTATION_EXISTS.key(), ""), ANNOTATION_EXISTS));
        assertFalse(isValidParam(new Query(ANNOTATION_EXISTS.key(), Collections.emptyList()), ANNOTATION_EXISTS));
        assertFalse(isValidParam(new Query(ANNOTATION_EXISTS.key(), Arrays.asList()), ANNOTATION_EXISTS));

        assertTrue(isValidParam(new Query(ANNOTATION_EXISTS.key(), Arrays.asList(1,2,3)), ANNOTATION_EXISTS));
        assertTrue(isValidParam(new Query(ANNOTATION_EXISTS.key(), 5), ANNOTATION_EXISTS));
        assertTrue(isValidParam(new Query(ANNOTATION_EXISTS.key(), "sdfas"), ANNOTATION_EXISTS));
    }

    @Test
    public void testParseSO() throws Exception {
        assertEquals(1587, parseConsequenceType("stop_gained"));
        assertEquals(1587, parseConsequenceType("1587"));
        assertEquals(1587, parseConsequenceType("SO:00001587"));
    }

    @Test
    public void testParseWrongSOTerm() throws Exception {
        thrown.expect(VariantQueryException.class);
        parseConsequenceType("wrong_so");
    }

    @Test
    public void testParseWrongSONumber() throws Exception {
        thrown.expect(VariantQueryException.class);
        parseConsequenceType("9999999");
    }

    @Test
    public void testParseWrongSONumber2() throws Exception {
        thrown.expect(VariantQueryException.class);
        parseConsequenceType("SO:9999999");
    }

    @Test
    public void testParseGenotypeFilter() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> expected = new HashMap(new ObjectMap()
                .append("study:sample", Arrays.asList("1/1", "2/2"))
                .append("sample2", Arrays.asList("0/0", "2/2"))
                .append("sample3", Arrays.asList("0/0"))
                .append("study1:sample4", Arrays.asList("0/0", "2/2")));

        HashMap<Object, List<String>> map = new HashMap<>();
        assertEquals(VariantQueryUtils.QueryOperation.AND, parseGenotypeFilter("study:sample:1/1,2/2;sample2:0/0,2/2;sample3:0/0;study1:sample4:0/0,2/2", map));
        assertEquals(expected, map);

        map = new HashMap<>();
        assertEquals(VariantQueryUtils.QueryOperation.OR, parseGenotypeFilter("study:sample:1/1,2/2,sample2:0/0,2/2,sample3:0/0,study1:sample4:0/0,2/2", map));
        assertEquals(expected, map);

        thrown.expect(VariantQueryException.class);
        parseGenotypeFilter("sample:1/1,2/2,sample2:0/0,2/2;sample3:0/0,2/2", map);
    }


}