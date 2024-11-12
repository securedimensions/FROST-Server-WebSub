/*
 * Copyright (C) 2024 Secure Dimensions GmbH, D-81377
 * Munich, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.securedimensions.frostserver.plugin.websub.test;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TestSuite for the executing all tests regarding WebSub Discovery.
 *
 * @author Andreas Matheus
 */

@SelectClasses({
    ActivationTests.DisabledTest.class,
    ActivationTests.EnabledTest.class,
    DiscoveryPathTests.DiscoveryPathTestsAll.class,
    DiscoveryPathTests.DiscoveryPathTestMD0.class,
    DiscoveryPathTests.DiscoveryPathTestMD1.class,
    DiscoveryQueryTests.DiscoveryWithQuery00.class,
    DiscoveryQueryTests.DiscoveryWithQuery01.class,
    DiscoveryQueryTests.DiscoveryWithQuery10.class,
    DiscoveryQueryTests.DiscoveryWithQuery11.class
})
@Suite
@Testcontainers
public class TestSuite {

}
