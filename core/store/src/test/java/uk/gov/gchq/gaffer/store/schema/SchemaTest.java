/*
 * Copyright 2016 Crown Copyright
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

package uk.gov.gchq.gaffer.store.schema;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import uk.gov.gchq.gaffer.commonutil.JsonUtil;
import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.commonutil.TestGroups;
import uk.gov.gchq.gaffer.commonutil.TestPropertyNames;
import uk.gov.gchq.gaffer.commonutil.TestTypes;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.Entity;
import uk.gov.gchq.gaffer.data.element.IdentifierType;
import uk.gov.gchq.gaffer.data.element.function.ElementAggregator;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.element.id.DirectedType;
import uk.gov.gchq.gaffer.data.elementdefinition.exception.SchemaException;
import uk.gov.gchq.gaffer.exception.SerialisationException;
import uk.gov.gchq.gaffer.function.ExampleAggregateFunction;
import uk.gov.gchq.gaffer.function.ExampleFilterFunction;
import uk.gov.gchq.gaffer.serialisation.Serialiser;
import uk.gov.gchq.gaffer.serialisation.ToBytesSerialiser;
import uk.gov.gchq.gaffer.serialisation.implementation.JavaSerialiser;
import uk.gov.gchq.koryphe.impl.predicate.IsA;
import uk.gov.gchq.koryphe.impl.predicate.IsXMoreThanY;
import uk.gov.gchq.koryphe.tuple.binaryoperator.TupleAdaptedBinaryOperator;
import uk.gov.gchq.koryphe.tuple.predicate.TupleAdaptedPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;


public class SchemaTest {
    public static final String EDGE_DESCRIPTION = "Edge description";
    public static final String ENTITY_DESCRIPTION = "Entity description";
    public static final String STRING_TYPE_DESCRIPTION = "String type description";
    public static final String INTEGER_TYPE_DESCRIPTION = "Integer type description";
    public static final String TIMESTAMP_TYPE_DESCRIPTION = "Timestamp type description";
    public static final String DATE_TYPE_DESCRIPTION = "Date type description";

    private Schema schema = new Schema.Builder().json(StreamUtil.schemas(getClass())).build();

    @Test
    public void shouldCloneSchema() throws SerialisationException {
        //Given

        // When
        final Schema clonedSchema = schema.clone();

        // Then
        // Check they are different instances
        assertNotSame(schema, clonedSchema);
        // Check they are equal by comparing the json
        JsonUtil.assertEquals(schema.toJson(true), clonedSchema.toJson(true));
    }

    @Test
    public void shouldDeserialiseAndReserialiseIntoTheSameJson() throws SerialisationException {
        //Given
        final byte[] json1 = schema.toCompactJson();
        final Schema schema2 = new Schema.Builder().json(json1).build();

        // When
        final byte[] json2 = schema2.toCompactJson();

        // Then
        JsonUtil.assertEquals(json1, json2);
    }

    @Test
    public void shouldDeserialiseAndReserialiseIntoTheSamePrettyJson() throws SerialisationException {
        //Given
        final byte[] json1 = schema.toJson(true);
        final Schema schema2 = new Schema.Builder().json(json1).build();

        // When
        final byte[] json2 = schema2.toJson(true);

        // Then
        JsonUtil.assertEquals(json1, json2);
    }

    @Test
    public void testLoadingSchemaFromJson() {
        // Edge definitions
        SchemaElementDefinition edgeDefinition = schema.getEdge(TestGroups.EDGE);
        assertNotNull(edgeDefinition);
        assertEquals(EDGE_DESCRIPTION, edgeDefinition.getDescription());

        final Map<String, String> propertyMap = edgeDefinition.getPropertyMap();
        assertEquals(3, propertyMap.size());
        assertEquals("prop.string", propertyMap.get(TestPropertyNames.PROP_2));
        assertEquals("prop.date", propertyMap.get(TestPropertyNames.DATE));
        assertEquals("timestamp", propertyMap.get(TestPropertyNames.TIMESTAMP));

        assertEquals(Sets.newLinkedHashSet(Collections.singletonList(TestPropertyNames.DATE)),
                edgeDefinition.getGroupBy());

        // Check validator
        ElementFilter validator = edgeDefinition.getValidator();
        List<TupleAdaptedPredicate<String, ?>> valContexts = validator.getComponents();
        int index = 0;

        TupleAdaptedPredicate<String, ?> tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(IdentifierType.SOURCE.name(), tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(IdentifierType.DESTINATION.name(), tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(IdentifierType.DIRECTED.name(), tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof ExampleFilterFunction);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(IdentifierType.DIRECTED.name(), tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.PROP_2, tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof ExampleFilterFunction);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.PROP_2, tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.DATE, tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.TIMESTAMP, tuplePredicate.getSelection()[0]);

        assertEquals(index, valContexts.size());

        TypeDefinition type = edgeDefinition.getPropertyTypeDef(TestPropertyNames.DATE);
        assertEquals(Date.class, type.getClazz());
        assertEquals(DATE_TYPE_DESCRIPTION, type.getDescription());
        assertNull(type.getSerialiser());
        assertTrue(type.getAggregateFunction() instanceof ExampleAggregateFunction);

        // Entity definitions
        SchemaElementDefinition entityDefinition = schema.getEntity(TestGroups.ENTITY);
        assertNotNull(entityDefinition);
        assertEquals(ENTITY_DESCRIPTION, entityDefinition.getDescription());
        assertTrue(entityDefinition.containsProperty(TestPropertyNames.PROP_1));
        type = entityDefinition.getPropertyTypeDef(TestPropertyNames.PROP_1);
        assertEquals(0, entityDefinition.getGroupBy().size());
        assertEquals(STRING_TYPE_DESCRIPTION, type.getDescription());
        assertEquals(String.class, type.getClazz());
        assertNull(type.getSerialiser());
        assertTrue(type.getAggregateFunction() instanceof ExampleAggregateFunction);
        validator = entityDefinition.getValidator();
        valContexts = validator.getComponents();
        index = 0;
        tuplePredicate = valContexts.get(index++);

        assertTrue(tuplePredicate.getPredicate() instanceof IsXMoreThanY);
        assertEquals(2, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.PROP_1, tuplePredicate.getSelection()[0]);
        assertEquals(TestPropertyNames.VISIBILITY, tuplePredicate.getSelection()[1]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(IdentifierType.VERTEX.name(), tuplePredicate.getSelection()[0]);

        tuplePredicate = valContexts.get(index++);
        assertTrue(tuplePredicate.getPredicate() instanceof IsA);
        assertEquals(1, tuplePredicate.getSelection().length);
        assertEquals(TestPropertyNames.PROP_1, tuplePredicate.getSelection()[0]);


        final ElementAggregator aggregator = edgeDefinition.getAggregator();
        final List<TupleAdaptedBinaryOperator<String, ?>> aggContexts = aggregator.getComponents();
        assertEquals(3, aggContexts.size());

        TupleAdaptedBinaryOperator<String, ?> aggContext = aggContexts.get(0);
        assertTrue(aggContext.getBinaryOperator() instanceof ExampleAggregateFunction);
        assertEquals(1, aggContext.getSelection().length);
        assertEquals(TestPropertyNames.PROP_2, aggContext.getSelection()[0]);

        aggContext = aggContexts.get(1);
        assertTrue(aggContext.getBinaryOperator() instanceof ExampleAggregateFunction);
        assertEquals(1, aggContext.getSelection().length);
        assertEquals(TestPropertyNames.DATE, aggContext.getSelection()[0]);
    }

    @Test
    public void shouldReturnTrueWhenSchemaHasAggregationEnabled() {
        final Schema schemaWithAggregators = new Schema.Builder()
                .entity(TestGroups.ENTITY, new SchemaEntityDefinition.Builder()
                        .aggregate(true)
                        .build())
                .build();
        assertTrue(schemaWithAggregators.isAggregationEnabled());
    }

    @Test
    public void shouldReturnFalseWhenSchemaHasAggregationDisabled() {
        final Schema schemaNoAggregators = new Schema.Builder()
                .entity(TestGroups.ENTITY, new SchemaEntityDefinition.Builder()
                        .aggregate(false)
                        .build())
                .build();
        assertFalse(schemaNoAggregators.isAggregationEnabled());
    }

    @Test
    public void createProgramaticSchema() {
        schema = createSchema();
    }

    private Schema createSchema() {
        return new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .source(TestTypes.ID_STRING)
                        .destination(TestTypes.ID_STRING)
                        .property(TestPropertyNames.PROP_1, TestTypes.PROP_STRING)
                        .property(TestPropertyNames.PROP_2, TestTypes.PROP_INTEGER)
                        .property(TestPropertyNames.TIMESTAMP, TestTypes.TIMESTAMP)
                        .groupBy(TestPropertyNames.PROP_1)
                        .description(EDGE_DESCRIPTION)
                        .validator(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_1)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .entity(TestGroups.ENTITY, new SchemaEntityDefinition.Builder()
                        .vertex(TestTypes.ID_STRING)
                        .property(TestPropertyNames.PROP_1, TestTypes.PROP_STRING)
                        .property(TestPropertyNames.PROP_2, TestTypes.PROP_INTEGER)
                        .property(TestPropertyNames.TIMESTAMP, TestTypes.TIMESTAMP)
                        .groupBy(TestPropertyNames.PROP_1)
                        .description(EDGE_DESCRIPTION)
                        .validator(new ElementFilter.Builder()
                                .select(TestPropertyNames.PROP_1)
                                .execute(new ExampleFilterFunction())
                                .build())
                        .build())
                .entity(TestGroups.ENTITY_2, new SchemaEntityDefinition.Builder()
                    .vertex(TestTypes.ID_STRING)
                    .property(TestPropertyNames.PROP_1, TestTypes.PROP_STRING)
                    .property(TestPropertyNames.PROP_2, TestTypes.PROP_INTEGER)
                    .property(TestPropertyNames.TIMESTAMP, TestTypes.TIMESTAMP)
                    .groupBy(TestPropertyNames.PROP_1, TestPropertyNames.PROP_2)
                    .description(ENTITY_DESCRIPTION)
                    .validator(new ElementFilter.Builder()
                            .select(TestPropertyNames.PROP_1)
                            .execute(new ExampleFilterFunction())
                            .build())
                    .build())
                .type(TestTypes.ID_STRING, new TypeDefinition.Builder()
                        .clazz(String.class)
                        .description(STRING_TYPE_DESCRIPTION)
                        .build())
                .type(TestTypes.PROP_STRING, new TypeDefinition.Builder()
                        .clazz(String.class)
                        .description(STRING_TYPE_DESCRIPTION)
                        .build())
                .type(TestTypes.PROP_INTEGER, new TypeDefinition.Builder()
                        .clazz(Integer.class)
                        .description(INTEGER_TYPE_DESCRIPTION)
                        .build())
                .type(TestTypes.TIMESTAMP, new TypeDefinition.Builder()
                        .clazz(Long.class)
                        .description(TIMESTAMP_TYPE_DESCRIPTION)
                        .build())
                .visibilityProperty(TestPropertyNames.VISIBILITY)
                .timestampProperty(TestPropertyNames.TIMESTAMP)
                .build();
    }

    @Test
    public void writeProgramaticSchemaAsJson() throws IOException, SchemaException {
        schema = createSchema();
        JsonUtil.assertEquals(String.format("{%n" +
                "  \"edges\" : {%n" +
                "    \"BasicEdge\" : {%n" +
                "      \"properties\" : {%n" +
                "        \"property1\" : \"prop.string\",%n" +
                "        \"property2\" : \"prop.integer\",%n" +
                "        \"timestamp\" : \"timestamp\"%n" +
                "      },%n" +
                "      \"groupBy\" : [ \"property1\" ],%n" +
                "      \"description\" : \"Edge description\",%n" +
                "      \"source\" : \"id.string\",%n" +
                "      \"destination\" : \"id.string\",%n" +
                "      \"validateFunctions\" : [ {%n" +
                "        \"predicate\" : {%n" +
                "          \"class\" : \"uk.gov.gchq.gaffer.function.ExampleFilterFunction\"%n" +
                "        },%n" +
                "        \"selection\" : [ \"property1\" ]%n" +
                "      } ]%n" +
                "    }%n" +
                "  },%n" +
                "  \"entities\" : {%n" +
                "    \"BasicEntity2\": {%n" +
                "      \"properties\": {%n" +
                "        \"property1\": \"prop.string\",%n" +
                "        \"property2\": \"prop.integer\",%n" +
                "        \"timestamp\": \"timestamp\"%n" +
                "      },%n" +
                "      \"groupBy\": [ \"property1\", \"property2\"],%n" +
                "      \"description\": \"Entity description\",%n" +
                "      \"vertex\": \"id.string\",%n" +
                "      \"validateFunctions\": [ {%n "+
                "        \"predicate\": {%n" +
                "          \"class\": \"uk.gov.gchq.gaffer.function.ExampleFilterFunction\"%n" +
                "        },%n" +
                "        \"selection\": [ \"property1\" ]%n" +
                "      } ]%n" +
                "    },%n" +
                "    \"BasicEntity\" : {%n" +
                "      \"properties\" : {%n" +
                "        \"property1\" : \"prop.string\",%n" +
                "        \"property2\" : \"prop.integer\",%n" +
                "        \"timestamp\" : \"timestamp\"%n" +
                "      },%n" +
                "      \"groupBy\" : [ \"property1\" ],%n" +
                "      \"description\" : \"Edge description\",%n" +
                "      \"vertex\" : \"id.string\",%n" +
                "      \"validateFunctions\" : [ {%n" +
                "        \"predicate\" : {%n" +
                "          \"class\" : \"uk.gov.gchq.gaffer.function.ExampleFilterFunction\"%n" +
                "        },%n" +
                "        \"selection\" : [ \"property1\" ]%n" +
                "      } ]%n" +
                "    }%n" +
                "  },%n" +
                "  \"types\" : {%n" +
                "    \"id.string\" : {%n" +
                "      \"description\" : \"String type description\",%n" +
                "      \"class\" : \"java.lang.String\"%n" +
                "    },%n" +
                "    \"prop.string\" : {%n" +
                "      \"description\" : \"String type description\",%n" +
                "      \"class\" : \"java.lang.String\"%n" +
                "    },%n" +
                "    \"prop.integer\" : {%n" +
                "      \"description\" : \"Integer type description\",%n" +
                "      \"class\" : \"java.lang.Integer\"%n" +
                "    },%n" +
                "    \"timestamp\" : {%n" +
                "      \"description\" : \"Timestamp type description\",%n" +
                "      \"class\" : \"java.lang.Long\"%n" +
                "    }%n" +
                "  },%n" +
                "  \"visibilityProperty\" : \"visibility\",%n" +
                "  \"timestampProperty\" : \"timestamp\"%n" +
                "}"), new String(schema.toJson(true)));
    }

    @Test
    public void testCorrectSerialiserRetrievableFromConfig() throws NotSerializableException {
        Schema store = new Schema.Builder()
                .type(TestTypes.PROP_STRING, new TypeDefinition.Builder()
                        .clazz(String.class)
                        .serialiser(new JavaSerialiser())
                        .build())
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_1, TestTypes.PROP_STRING)
                        .build())
                .build();

        assertEquals(JavaSerialiser.class,
            store.getElement(TestGroups.EDGE)
                .getPropertyTypeDef(TestPropertyNames.PROP_1)
                .getSerialiser()
                .getClass());
    }

    @Test
    public void testStoreConfigUsableWithSchemaInitialisationAndProgramaticListOfElements() {
        final SchemaEntityDefinition entityDef = new SchemaEntityDefinition.Builder()
                .property(TestPropertyNames.PROP_1, TestTypes.PROP_STRING)
                .build();

        final SchemaEdgeDefinition edgeDef = new SchemaEdgeDefinition.Builder()
                .property(TestPropertyNames.PROP_2, TestTypes.PROP_STRING)
                .build();

        final Schema schema = new Schema.Builder()
                .type(TestTypes.PROP_STRING, String.class)
                .type(TestTypes.PROP_STRING, Integer.class)
                .entity(TestGroups.ENTITY, entityDef)
                .edge(TestGroups.EDGE, edgeDef)
                .build();

        assertSame(entityDef, schema.getEntity(TestGroups.ENTITY));
        assertSame(edgeDef, schema.getEdge(TestGroups.EDGE));
    }

    @Test
    public void testSchemaConstructedFromInputStream() throws IOException {
        final InputStream resourceAsStream = this.getClass().getResourceAsStream(StreamUtil.DATA_SCHEMA);
        assertNotNull(resourceAsStream);
        final Schema deserialisedSchema = new Schema.Builder().json(resourceAsStream).build();
        assertNotNull(deserialisedSchema);

        final Map<String, SchemaEdgeDefinition> edges = deserialisedSchema.getEdges();

        assertEquals(1, edges.size());
        final SchemaElementDefinition edgeGroup = edges.get(TestGroups.EDGE);
        assertEquals(3, edgeGroup.getProperties().size());

        final Map<String, SchemaEntityDefinition> entities = deserialisedSchema.getEntities();

        assertEquals(1, entities.size());
        final SchemaElementDefinition entityGroup = entities.get(TestGroups.ENTITY);
        assertEquals(3, entityGroup.getProperties().size());

        assertEquals(TestPropertyNames.VISIBILITY, deserialisedSchema.getVisibilityProperty());
        assertEquals(TestPropertyNames.TIMESTAMP, deserialisedSchema.getTimestampProperty());
    }

    @Test
    public void shouldBuildSchema() {
        // Given
        final Serialiser vertexSerialiser = mock(Serialiser.class);

        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE)
                .entity(TestGroups.ENTITY)
                .entity(TestGroups.ENTITY_2)
                .edge(TestGroups.EDGE_2)
                .vertexSerialiser(vertexSerialiser)
                .type(TestTypes.PROP_STRING, String.class)
                .visibilityProperty(TestPropertyNames.VISIBILITY)
                .build();

        // Then
        assertEquals(2, schema.getEdges().size());
        assertNotNull(schema.getEdge(TestGroups.EDGE));
        assertNotNull(schema.getEdge(TestGroups.EDGE_2));

        assertEquals(2, schema.getEntities().size());
        assertNotNull(schema.getEntity(TestGroups.ENTITY));
        assertNotNull(schema.getEntity(TestGroups.ENTITY_2));

        assertEquals(String.class, schema.getType(TestTypes.PROP_STRING).getClazz());
        assertSame(vertexSerialiser, schema.getVertexSerialiser());

        assertEquals(TestPropertyNames.VISIBILITY, schema.getVisibilityProperty());
    }

    @Test
    public void shouldMergeDifferentSchemas() {
        // Given
        final String type1 = "type1";
        final String type2 = "type2";
        final Serialiser vertexSerialiser = mock(Serialiser.class);
        final Schema schema1 = new Schema.Builder()
                .edge(TestGroups.EDGE)
                .entity(TestGroups.ENTITY)
                .vertexSerialiser(vertexSerialiser)
                .type(type1, Integer.class)
                .visibilityProperty(TestPropertyNames.VISIBILITY)
                .build();

        final Schema schema2 = new Schema.Builder()
                .entity(TestGroups.ENTITY_2)
                .edge(TestGroups.EDGE_2)
                .type(type2, String.class)
                .build();

        // When
        final Schema mergedSchema = new Schema.Builder()
                .merge(schema1)
                .merge(schema2)
                .build();

        // Then
        assertEquals(2, mergedSchema.getEdges().size());
        assertNotNull(mergedSchema.getEdge(TestGroups.EDGE));
        assertNotNull(mergedSchema.getEdge(TestGroups.EDGE_2));

        assertEquals(2, mergedSchema.getEntities().size());
        assertNotNull(mergedSchema.getEntity(TestGroups.ENTITY));
        assertNotNull(mergedSchema.getEntity(TestGroups.ENTITY_2));

        assertEquals(Integer.class, mergedSchema.getType(type1).getClazz());
        assertEquals(String.class, mergedSchema.getType(type2).getClazz());
        assertSame(vertexSerialiser, mergedSchema.getVertexSerialiser());
        assertEquals(TestPropertyNames.VISIBILITY, mergedSchema.getVisibilityProperty());
    }

    @Test
    public void shouldMergeDifferentSchemasOppositeWayAround() {
        // Given
        final String type1 = "type1";
        final String type2 = "type2";
        final Serialiser vertexSerialiser = mock(Serialiser.class);
        final Schema schema1 = new Schema.Builder()
                .edge(TestGroups.EDGE)
                .entity(TestGroups.ENTITY)
                .vertexSerialiser(vertexSerialiser)
                .type(type1, Integer.class)
                .visibilityProperty(TestPropertyNames.VISIBILITY)
                .build();

        final Schema schema2 = new Schema.Builder()
                .entity(TestGroups.ENTITY_2)
                .edge(TestGroups.EDGE_2)
                .type(type2, String.class)
                .build();

        // When
        final Schema mergedSchema = new Schema.Builder()
                .merge(schema2)
                .merge(schema1)
                .build();

        // Then
        assertEquals(2, mergedSchema.getEdges().size());
        assertNotNull(mergedSchema.getEdge(TestGroups.EDGE));
        assertNotNull(mergedSchema.getEdge(TestGroups.EDGE_2));

        assertEquals(2, mergedSchema.getEntities().size());
        assertNotNull(mergedSchema.getEntity(TestGroups.ENTITY));
        assertNotNull(mergedSchema.getEntity(TestGroups.ENTITY_2));

        assertEquals(Integer.class, mergedSchema.getType(type1).getClazz());
        assertEquals(String.class, mergedSchema.getType(type2).getClazz());
        assertSame(vertexSerialiser, mergedSchema.getVertexSerialiser());
        assertEquals(TestPropertyNames.VISIBILITY, mergedSchema.getVisibilityProperty());
    }

    @Test
    public void shouldThrowExceptionWhenMergeSchemasWithASharedEdgeGroup() {
        // Given
        final Schema schema1 = new Schema.Builder()
                .edge(TestGroups.EDGE)
                .build();
        final Schema schema2 = new Schema.Builder()
                .edge(TestGroups.EDGE)
                .build();

        // When / Then
        try {
            new Schema.Builder()
                    .merge(schema1)
                    .merge(schema2);
            fail("Exception expected");
        } catch (final SchemaException e) {
            assertTrue(e.getMessage().contains("Element groups cannot be shared"));
        }
    }

    @Test
    public void shouldThrowExceptionWhenMergeSchemasWithASharedEntityGroup() {
        // Given
        final Schema schema1 = new Schema.Builder()
                .entity(TestGroups.ENTITY)
                .build();
        final Schema schema2 = new Schema.Builder()
                .entity(TestGroups.ENTITY)
                .build();

        // When / Then
        try {
            new Schema.Builder()
                    .merge(schema1)
                    .merge(schema2);
            fail("Exception expected");
        } catch (final SchemaException e) {
            assertTrue(e.getMessage().contains("Element groups cannot be shared"));
        }
    }

    @Test
    public void shouldThrowExceptionWhenMergeSchemasWithConflictingVertexSerialiser() {
        // Given
        final Serialiser vertexSerialiser1 = mock(Serialiser.class);
        final Serialiser vertexSerialiser2 = mock(SerialisationImpl.class);
        final Schema schema1 = new Schema.Builder()
                .vertexSerialiser(vertexSerialiser1)
                .build();
        final Schema schema2 = new Schema.Builder()
                .vertexSerialiser(vertexSerialiser2)
                .build();

        // When / Then
        try {
            new Schema.Builder()
                    .merge(schema1)
                    .merge(schema2)
                    .build();
            fail("Exception expected");
        } catch (final SchemaException e) {
            assertTrue(e.getMessage().contains("vertex serialiser"));
        }
    }

    @Test
    public void shouldThrowExceptionWhenMergeSchemasWithConflictingVisibility() {
        // Given
        final Schema schema1 = new Schema.Builder()
                .visibilityProperty(TestPropertyNames.VISIBILITY)
                .build();
        final Schema schema2 = new Schema.Builder()
                .visibilityProperty(TestPropertyNames.COUNT)
                .build();

        // When / Then
        try {
            new Schema.Builder()
                    .merge(schema1)
                    .merge(schema2)
                    .build();
            fail("Exception expected");
        } catch (final SchemaException e) {
            assertTrue(e.getMessage().contains("visibility property"));
        }
    }

    @Test
    public void shouldNotRemoveMissingParentsWhenExpanded() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                    .parents(TestGroups.EDGE)
                    .build())
                .build();

        // Then
        assertArrayEquals(new String[]{TestGroups.EDGE}, schema.getEdge(TestGroups.EDGE_2).getParents().toArray());
    }

    @Test
    public void shouldInheritIdentifiersFromParents() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .source("string")
                        .destination("int")
                        .build())
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                        .parents(TestGroups.EDGE)
                        .destination("long")
                        .directed("true")
                        .build())
                .merge(new Schema.Builder()
                        .edge(TestGroups.EDGE_3, new SchemaEdgeDefinition.Builder()
                                .parents(TestGroups.EDGE_2, TestGroups.EDGE)
                                .source("date")
                                .build())
                        .build())
                .build();

        // Then
        final SchemaEdgeDefinition childEdge1 = schema.getEdge(TestGroups.EDGE);
        assertEquals("string", childEdge1.getSource());
        assertEquals("int", childEdge1.getDestination());
        assertEquals(null, childEdge1.getDirected());

        final SchemaEdgeDefinition childEdge2 = schema.getEdge(TestGroups.EDGE_2);
        assertEquals("string", childEdge2.getSource());
        assertEquals("long", childEdge2.getDestination());
        assertEquals("true", childEdge2.getDirected());

        final SchemaEdgeDefinition childEdge3 = schema.getEdge(TestGroups.EDGE_3);
        assertEquals("date", childEdge3.getSource());
        assertEquals("int", childEdge3.getDestination());
        assertEquals("true", childEdge3.getDirected());
    }

    @Test
    public void shouldInheritPropertiesFromParentsInOrderFromJson() {
        // When
        final Schema schema = new Schema.Builder()
                .json(StreamUtil.openStream(getClass(), "schemaWithParents.json"))
                .build();

        // Then
        // Check edges
        assertArrayEquals(new String[]{
                        TestPropertyNames.PROP_1,
                        TestPropertyNames.PROP_2,
                        TestPropertyNames.PROP_3,
                        TestPropertyNames.PROP_4},
                schema.getEdge(TestGroups.EDGE_4).getProperties().toArray());

        // Check order of properties and overrides is from order of parents
        assertArrayEquals(new String[]{
                        TestPropertyNames.PROP_1,
                        TestPropertyNames.PROP_2,
                        TestPropertyNames.PROP_3,
                        TestPropertyNames.PROP_4,
                        TestPropertyNames.PROP_5},
                schema.getEdge(TestGroups.EDGE_5).getProperties().toArray());

        assertEquals("A parent edge with a single property", schema.getEdge(TestGroups.EDGE).getDescription());
        assertEquals("An edge that should have properties: 1, 2, 3, 4 and 5", schema.getEdge(TestGroups.EDGE_5).getDescription());
        assertArrayEquals(new String[]{TestPropertyNames.PROP_1}, schema.getEdge(TestGroups.EDGE).getGroupBy().toArray());
        assertArrayEquals(new String[]{TestPropertyNames.PROP_4}, schema.getEdge(TestGroups.EDGE_5).getGroupBy().toArray());

        // Check entities
        assertArrayEquals(new String[]{
                TestPropertyNames.PROP_1,
                TestPropertyNames.PROP_2,
                TestPropertyNames.PROP_3,
                TestPropertyNames.PROP_4},
            schema.getEntity(TestGroups.ENTITY_4).getProperties().toArray());

        // Check order of properties and overrides is from order of parents
        assertArrayEquals(new String[]{
                TestPropertyNames.PROP_1,
                TestPropertyNames.PROP_2,
                TestPropertyNames.PROP_3,
                TestPropertyNames.PROP_4,
                TestPropertyNames.PROP_5},
            schema.getEntity(TestGroups.ENTITY_5).getProperties().toArray());

        assertEquals("A parent entity with a single property", schema.getEntity(TestGroups.ENTITY).getDescription());
        assertEquals("An entity that should have properties: 1, 2, 3, 4 and 5", schema.getEntity(TestGroups.ENTITY_5).getDescription());
        assertArrayEquals(new String[]{TestPropertyNames.PROP_1}, schema.getEntity(TestGroups.ENTITY).getGroupBy().toArray());
        assertArrayEquals(new String[]{TestPropertyNames.PROP_4}, schema.getEntity(TestGroups.ENTITY_5).getGroupBy().toArray());
    }

    @Test
    public void shouldInheritPropertiesFromParentsInOrder() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_1, "prop.string")
                        .build())
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_2, "prop.string2")
                        .build())
                .edge(TestGroups.EDGE_3, new SchemaEdgeDefinition.Builder()
                        .parents(TestGroups.EDGE, TestGroups.EDGE_2)
                        .property(TestPropertyNames.PROP_3, "prop.string3")
                        .build())
                .edge(TestGroups.EDGE_4, new SchemaEdgeDefinition.Builder()
                        .parents(TestGroups.EDGE_3)
                        .property(TestPropertyNames.PROP_4, "prop.string4")
                        .build())
                .edge(TestGroups.EDGE_5, new SchemaEdgeDefinition.Builder()
                        .parents(TestGroups.EDGE_4)
                        .property(TestPropertyNames.PROP_5, "prop.string5")
                        .build())
                .build();


        // Then
        assertArrayEquals(new String[]{
                        TestPropertyNames.PROP_1,
                        TestPropertyNames.PROP_2,
                        TestPropertyNames.PROP_3,
                        TestPropertyNames.PROP_4},
                schema.getEdge(TestGroups.EDGE_4).getProperties().toArray());

        // Then - check order of properties and overrides is from order of parents
        assertArrayEquals(new String[]{
                        TestPropertyNames.PROP_1,
                        TestPropertyNames.PROP_2,
                        TestPropertyNames.PROP_3,
                        TestPropertyNames.PROP_4,
                        TestPropertyNames.PROP_5},
                schema.getEdge(TestGroups.EDGE_5).getProperties().toArray());
    }

    @Test
    public void shouldThrowExceptionIfPropertyExistsInParentAndChild() {
        // When / Then
        try {
            new Schema.Builder()
                    .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                            .property(TestPropertyNames.PROP_1, "prop.string")
                            .property(TestPropertyNames.PROP_2, "prop.integer")
                            .build())
                    .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                            .parents(TestGroups.EDGE)
                            .property(TestPropertyNames.PROP_1, "prop.string.changed")
                            .build())
                    .build();
            fail("Exception expected");
        } catch (final SchemaException e) {
            assertNotNull(e);
        }
    }

    @Test
    public void shouldOverrideInheritedParentGroupBy() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_1, "prop.string")
                        .property(TestPropertyNames.PROP_2, "prop.integer")
                        .groupBy(TestPropertyNames.PROP_1)
                        .build())
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                        .groupBy(TestPropertyNames.PROP_2)
                        .parents(TestGroups.EDGE)
                        .build())
                .build();

        // Then
        assertArrayEquals(new String[]{TestPropertyNames.PROP_2},
                schema.getEdge(TestGroups.EDGE_2).getGroupBy().toArray());
    }

    @Test
    public void shouldOverrideInheritedParentGroupByEvenWhenEmpty() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_1, "prop.string")
                        .property(TestPropertyNames.PROP_2, "prop.integer")
                        .groupBy(TestPropertyNames.PROP_1)
                        .build())
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                        .groupBy()
                        .parents(TestGroups.EDGE)
                        .build())
                .build();

        // Then
        assertArrayEquals(new String[0],
                schema.getEdge(TestGroups.EDGE_2).getGroupBy().toArray());
    }

    @Test
    public void shouldOverrideInheritedParentGroupByEvenWhenNotSet() {
        // When
        final Schema schema = new Schema.Builder()
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .property(TestPropertyNames.PROP_1, "prop.string")
                        .property(TestPropertyNames.PROP_2, "prop.integer")
                        .groupBy(TestPropertyNames.PROP_1)
                        .build())
                .edge(TestGroups.EDGE_2, new SchemaEdgeDefinition.Builder()
                        .parents(TestGroups.EDGE)
                        .build())
                .build();

        // Then
        assertArrayEquals(new String[0],
                schema.getEdge(TestGroups.EDGE_2).getGroupBy().toArray());
    }

    @Test
    public void shouldSerialiseToCompactJson() {
        // Given - schema loaded from file

        // When
        final String compactJson = new String(schema.toCompactJson());

        // Then - no description fields or new lines
        assertFalse(compactJson.contains("description"));
        assertFalse(compactJson.contains(String.format("%n")));
    }

    @Test
    public void shouldGetAllGroups() {
        // Given - schema loaded from file

        // When
        final Set<String> groups = schema.getGroups();

        // Then
        final Set<String> allGroups = new HashSet<>(schema.getEntityGroups());
        allGroups.addAll(schema.getEdgeGroups());

        assertEquals(allGroups, groups);
    }

    @Test
    public void shouldCollectAllElementsTogetherIfNoGroupByIsStated() {
        // given
        Schema schema = Schema.fromJson(StreamUtil.openStream(getClass(), "/schema/dataSchema.json"));

        // when
        Function<Element, Set<Object>> fn = schema.createGroupByFunction();

        List<Element> input = Arrays.asList(
            new Entity.Builder()
                .group(TestGroups.ENTITY)
                .vertex("vertex1")
                .build(),
            new Entity.Builder()
                .group(TestGroups.ENTITY)
                .vertex("vertex2")
                .build()
        );
        // then

        Map<Set<Object>, List<Element>> results = input.stream().collect(Collectors.groupingBy(fn));
        Map<Set<Object>, List<Element>> expected = new HashMap<>();
        expected.put(new HashSet<>(Lists.newArrayList("vertex1", TestGroups.ENTITY)), Lists.newArrayList(input.get(0)));
        expected.put(new HashSet<>(Lists.newArrayList("vertex2", TestGroups.ENTITY)), Lists.newArrayList(input.get(1)));

        assertEquals(expected, results);
    }

    @Test
    public void shouldCollectElementsTogetherIfOneGroupByValueSpecified() {
        // given
        Schema schema = createSchema();

        // when
        Function<Element, Set<Object>> fn = schema.createGroupByFunction();
        List<Element> input = Arrays.asList(
            new Entity.Builder()
                .group(TestGroups.ENTITY)
                .vertex("vertex1")
                .property(TestPropertyNames.PROP_1, "test1")
                .build(),
            new Entity.Builder()
                .group(TestGroups.ENTITY)
                .vertex("vertex2")
                .property(TestPropertyNames.PROP_1, "test2")
                .build(),
            new Edge.Builder()
                .group(TestGroups.EDGE)
                .source("vertex1")
                .dest("vertex2")
                .property(TestPropertyNames.PROP_1, "test2")
                .build()
        );

        // then

        Map<Set<Object>, List<Element>> results = input.stream().collect(Collectors.groupingBy(fn));
        Map<Set<Object>, List<Element>> expected = new HashMap<>();
        expected.put(Sets.newHashSet("test1", "vertex1", TestGroups.ENTITY), Collections.singletonList(input.get(0)));
        expected.put(Sets.newHashSet("test2", "vertex2", TestGroups.ENTITY), Lists.newArrayList(input.get(1)));
        expected.put(Sets.newHashSet("test2", "vertex1", "vertex2", DirectedType.UNDIRECTED, TestGroups.EDGE), Lists.newArrayList(input.get(2)));

        assertEquals(expected, results);

    }

    @Test
    public void shouldCollectElementsTogetherWhenMoreThanOneGroupByPropertyIsSpecified() {
        // given
        Schema schema = createSchema();

        // when

        Function<Element, Set<Object>> fn = schema.createGroupByFunction();
        List<Element> input = Arrays.asList(
            new Entity.Builder()
                .group(TestGroups.ENTITY_2)
                .vertex("vertex1")
                .property(TestPropertyNames.PROP_1, "test1")
                .property(TestPropertyNames.PROP_2, 1)
                .build(),
            new Entity.Builder()
                .group(TestGroups.ENTITY_2)
                .vertex("vertex2")
                .property(TestPropertyNames.PROP_1, "test1")
                .property(TestPropertyNames.PROP_2, 2)
                .build(),
            new Entity.Builder()
                .group(TestGroups.ENTITY_2)
                .vertex("vertex2")
                .property(TestPropertyNames.PROP_1, "test1")
                .property(TestPropertyNames.PROP_2, 1)
                .build(),
            new Entity.Builder()
                .group(TestGroups.ENTITY_2)
                .vertex("vertex2")
                .property(TestPropertyNames.PROP_1, "test2")
                .property(TestPropertyNames.PROP_2, 2)
                .build()
        );

        // then

        Map<Set<Object>, List<Element>> results = input.stream().collect(Collectors.groupingBy(fn));
        Map<Set<Object>, List<Element>> expected = new HashMap<>();
        expected.put(Sets.newHashSet("test1", 1, "vertex1", TestGroups.ENTITY_2), Lists.newArrayList(input.get(0)));
        expected.put(Sets.newHashSet("test1", 1, "vertex2", TestGroups.ENTITY_2), Lists.newArrayList(input.get(2)));
        expected.put(Sets.newHashSet("test1", 2, "vertex2", TestGroups.ENTITY_2), Lists.newArrayList(input.get(1)));
        expected.put(Sets.newHashSet("test2", 2, "vertex2", TestGroups.ENTITY_2), Lists.newArrayList(input.get(3)));


        assertEquals(expected, results);

    }

    @Test
    public void shouldThrowExceptionIfElementBelongsToGroupThatDoesntExistInSchema() {
        // given
        Schema schema = createSchema();

        // when
        List<Element> elements = Lists.newArrayList(
            new Entity.Builder()
                .group("Unknown group")
                .vertex("vertex1")
                .property("Meaning of life", 42)
                .build()
        );

        Function<Element, Set<Object>> fn = schema.createGroupByFunction();
        // then
        try {
            Map<Set<Object>, List<Element>> results = elements.stream().collect(Collectors.groupingBy(fn));
        } catch (RuntimeException e) {
            assertNotNull(e.getMessage());
        }

    }

    private class SerialisationImpl implements ToBytesSerialiser<Object> {
        private static final long serialVersionUID = 5055359689222968046L;

        @Override
        public boolean canHandle(final Class clazz) {
            return false;
        }

        @Override
        public byte[] serialise(final Object object) throws SerialisationException {
            return new byte[0];
        }

        @Override
        public Object deserialise(final byte[] bytes) throws SerialisationException {
            return null;
        }

        @Override
        public Object deserialiseEmpty() throws SerialisationException {
            return null;
        }

        @Override
        public boolean preservesObjectOrdering() {
            return true;
        }
    }
}