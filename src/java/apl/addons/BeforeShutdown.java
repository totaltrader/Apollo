/*
 * Copyright © 2013-2016 The Apl Core Developers.
 * Copyright © 2016-2017 Apollo Foundation IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Apollo Foundation B.V.,
 * no part of the Apl software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package apl.addons;

import apl.Apl;
import apl.util.Logger;

public final class BeforeShutdown implements AddOn {

    final String beforeShutdownScript = Apl.getStringProperty("apl.beforeShutdownScript");

    @Override
    public void shutdown() {
        if (beforeShutdownScript != null) {
            try {
                Runtime.getRuntime().exec(beforeShutdownScript);
            } catch (Exception e) {
                Logger.logShutdownMessage("Failed to run after start script: " + beforeShutdownScript, e);
            }
        }
    }

}
