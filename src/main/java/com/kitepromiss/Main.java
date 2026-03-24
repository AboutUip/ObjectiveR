package com.kitepromiss;

import com.kitepromiss.obr.LaunchArgs;
import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ObrInterpreter;
import com.kitepromiss.obr.trace.InterpreterAuditLog;

/** CLI 入口：BlinkEngine（{@link com.kitepromiss.obr.ObrInterpreter}）。 */
public final class Main {

    public static void main(String[] args) {
        try {
            LaunchArgs la = LaunchArgs.parse(args);
            InterpreterAuditLog log = InterpreterAuditLog.toStdout(la.tracePolicy());
            System.exit(new ObrInterpreter(log).run(la));
        } catch (ObrException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
