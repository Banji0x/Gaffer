/*
 * Copyright 2017 Crown Copyright
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

package uk.gov.gchq.gaffer.store.operation.handler.named;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import uk.gov.gchq.gaffer.cache.CacheServiceLoader;
import uk.gov.gchq.gaffer.commonutil.TestGroups;
import uk.gov.gchq.gaffer.data.elementdefinition.view.NamedView;
import uk.gov.gchq.gaffer.named.operation.cache.exception.CacheOperationFailedException;
import uk.gov.gchq.gaffer.named.view.AddNamedView;
import uk.gov.gchq.gaffer.named.view.DeleteNamedView;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.Store;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.operation.handler.named.cache.NamedViewCache;
import uk.gov.gchq.gaffer.user.User;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class DeleteNamedViewHandlerTest {
    private final NamedViewCache namedViewCache = new NamedViewCache();
    private final AddNamedViewHandler addNamedViewHandler = new AddNamedViewHandler(namedViewCache);
    private final DeleteNamedViewHandler deleteNamedViewHandler = new DeleteNamedViewHandler(namedViewCache);
    private final String testNamedViewName = "testNamedViewName";
    private final String testUserId = "testUser";
    private final Map<String, Object> testParameters = new HashMap<>();

    private Context context = new Context(new User.Builder()
            .userId(testUserId)
            .build());

    private Store store = mock(Store.class);

    NamedView namedView;

    AddNamedView addNamedView;

    @Before
    public void before() throws OperationException {
        testParameters.put("testParam", "testKey");

        namedView = new NamedView.Builder()
                .name(testNamedViewName)
                .edge(TestGroups.EDGE)
                .parameters(testParameters)
                .build();

        addNamedView = new AddNamedView.Builder()
                .namedView(namedView)
                .overwrite(false)
                .build();

        StoreProperties properties = new StoreProperties();
        properties.set("gaffer.cache.service.class", "uk.gov.gchq.gaffer.cache.impl.HashMapCacheService");
        CacheServiceLoader.initialise(properties.getProperties());

        addNamedViewHandler.doOperation(addNamedView, context, store);
    }

    @AfterClass
    public static void tearDown() {
        CacheServiceLoader.shutdown();
    }

    @Test
    public void shouldAddNamedViewCorrectly() throws OperationException, CacheOperationFailedException {

        assertTrue(cacheContains(testNamedViewName));

        final DeleteNamedView deleteNamedView = new DeleteNamedView.Builder().name(testNamedViewName).build();

        deleteNamedViewHandler.doOperation(deleteNamedView, context, store);

        assertFalse(cacheContains(testNamedViewName));
    }

    private boolean cacheContains(final String namedViewName) throws CacheOperationFailedException {
        Iterable<NamedView> namedViews = namedViewCache.getAllNamedViews();
        for (final NamedView namedView : namedViews) {
            if (namedView.getName().equals(namedViewName)) {
                return true;
            }
        }
        return false;
    }
}
