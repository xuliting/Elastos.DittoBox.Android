/**
 *   ownCloud Android client application
 *
 *   @author purigarcia
 *   Copyright (C) 2016 ownCloud GmbH.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.elastos.ditto.test.ui.testSuites;


import org.junit.experimental.categories.Categories.ExcludeCategory;
import org.junit.experimental.categories.Categories.IncludeCategory;
import org.junit.runner.RunWith;

import org.elastos.ditto.test.ui.groups.FailingTestCategory;
import org.elastos.ditto.test.ui.groups.FlexibleCategories;
import org.elastos.ditto.test.ui.groups.NoIgnoreTestCategory;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestClassPrefix;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestClassSuffix;
import org.elastos.ditto.test.ui.groups.FlexibleCategories.TestScanPackage;


@RunWith(FlexibleCategories.class)
@ExcludeCategory(NoIgnoreTestCategory.class)
@IncludeCategory(FailingTestCategory.class)
@TestScanPackage("com.owncloud.android.test.ui.testSuites")
@TestClassPrefix("")
@TestClassSuffix("TestSuite")
public class RunFailingTests {

}
