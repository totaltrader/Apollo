package com.apollocurrency.aplwallet.apl.updater.pdu;

import com.apollocurrency.aplwallet.apl.util.Platform;
import com.apollocurrency.aplwallet.apl.updater.UpdateInfo;

public interface PlatformDependentUpdaterFactory {
    PlatformDependentUpdater createInstance(Platform platform, UpdateInfo updateInfo);
}
