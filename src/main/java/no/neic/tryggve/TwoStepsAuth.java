package no.neic.tryggve;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.apache.commons.lang3.ArrayUtils;

public final class TwoStepsAuth implements UserInfo, UIKeyboardInteractive {
    private String password;
    private String otc;

    public TwoStepsAuth(String password, String otc) {
        this.password = password;
        this.otc = otc;
    }

    @Override
    public String[] promptKeyboardInteractive(String destination, String name,
                                              String instruction, String[] prompt, boolean[] echo) {
        if (ArrayUtils.isEmpty(prompt)) {
            return new String[0];
        } else {
            if (prompt[0].trim().toLowerCase().contains("password")) {
                return new String[]{password};
            } else {
                return new String[]{otc};
            }
        }
    }

    @Override
    public String getPassphrase() {
        return null;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean promptPassword(String s) {
        return false;
    }

    @Override
    public boolean promptPassphrase(String s) {
        return false;
    }

    @Override
    public boolean promptYesNo(String s) {
        return false;
    }

    @Override
    public void showMessage(String s) {
    }
}