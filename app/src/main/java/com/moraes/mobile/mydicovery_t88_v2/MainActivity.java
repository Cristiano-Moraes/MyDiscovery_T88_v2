package com.moraes.mobile.mydicovery_t88_v2;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.epson.epos2.Epos2Exception;
import com.epson.epos2.Log;
import com.epson.epos2.discovery.DeviceInfo;
import com.epson.epos2.discovery.Discovery;
import com.epson.epos2.discovery.DiscoveryListener;
import com.epson.epos2.discovery.FilterOption;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.PrinterStatusInfo;
import com.epson.epos2.printer.ReceiveListener;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;

    private Context mContext = null;
    private FilterOption mFilterOption = null;
    public static Printer mPrinter = null;
    String ptn = "";
    String tgn = "";
    String mac = "";
    String device ="";
    TextView viewDevice;
    TextView viewMac;
    TextView txtprintname = null;
    TextView txtTarget = null;
    Button buttonStart = null;
    Button buttonStop = null;
    Button btnPrint = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRuntimePermission();

        mContext = this;


        txtprintname = findViewById(R.id.txtTempPrinterName);
        txtTarget = findViewById(R.id.txtTempTarget);
        viewDevice = findViewById(R.id.PrinterName);
        viewMac = findViewById(R.id.lblTargetMAC);
        txtprintname.setText(ptn);
        txtTarget.setText(tgn);

        buttonStart = findViewById(R.id.btnStart);
        buttonStop = findViewById(R.id.btnStop);
        btnPrint = findViewById(R.id.buttonPrint);
        buttonStop.setEnabled(false);
        btnPrint.setEnabled(false);

        buttonStart.setOnClickListener( new Button.OnClickListener(){
            public void onClick( View v ){
                buttonStart.setEnabled(false);
                ptn = "";
                tgn = "";
                mac = "";
                device ="";
                startDiscovery();
                buttonStop.setEnabled(true);
            }
        });

        buttonStop.setOnClickListener(new Button.OnClickListener(){
            public void onClick( View v ){
                stopDiscovery();
                btnPrint.setEnabled(true);
                txtprintname.setText(ptn);
                txtTarget.setText(tgn);
                viewMac.setText(mac);
                viewDevice.setText(device);

            }
        });

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runPrintReceiptSequence();
            }
        });


        try {
            Log.setLogSettings(mContext, Log.PERIOD_TEMPORARY, Log.OUTPUT_STORAGE, null, 0, 1, Log.LOGLEVEL_LOW);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "setLogSettings", mContext);
        }


    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        while (true) {
            try {
                Discovery.stop();
                break;
            } catch (Epos2Exception e) {
                if (e.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                    break;
                }
            }
        }

        mFilterOption = null;
    }


    private DiscoveryListener mDiscoveryListener = new DiscoveryListener() {
        @Override
        public void onDiscovery(final DeviceInfo deviceInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public synchronized void run() {
                    ptn = deviceInfo.getDeviceName();
                    tgn = deviceInfo.getTarget();
                    mac = deviceInfo.getMacAddress();
                    device = String.valueOf(deviceInfo.getDeviceType());

                }
            });
        }
    };


    private void startDiscovery() {


        //FilterOption filterOption = null;
        //mPrinterList.clear();
        //mPrinterListAdapter.notifyDataSetChanged();
        mFilterOption = new FilterOption();
        mFilterOption.getDeviceType();
        mFilterOption.getPortType();
        mFilterOption.getEpsonFilter();
        //mFilterOption.getDeviceModel();
        // mFilterOption.setDeviceType(Discovery.TYPE_PRINTER);
        // mFilterOption.setEpsonFilter(Discovery.FILTER_NAME);
        /*
        filterOption = new FilterOption();
        filterOption.setPortType(((SpnModelsItem) mSpnPortType.getSelectedItem()).getModelConstant());
        filterOption.setBroadcast(mEdtBroadCast.getText().toString());
        filterOption.setDeviceModel(((SpnModelsItem)mSpnModel.getSelectedItem()).getModelConstant());
        filterOption.setEpsonFilter(((SpnModelsItem)mSpnFilter.getSelectedItem()).getModelConstant());
        filterOption.setDeviceType(((SpnModelsItem)mSpnType.getSelectedItem()).getModelConstant());
        */

        try {
            Discovery.start(mContext, mFilterOption, mDiscoveryListener);
            buttonStart.setEnabled(false);
            buttonStop.setEnabled(true);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "start", mContext);
        }
    }

    private void stopDiscovery() {
        try {
            Discovery.stop();
        }
        catch (Epos2Exception e) {
            if (e.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                ShowMsg.showException(e, "stop", mContext);
            }
        }
    }

    private boolean initializeObject() {
        try {
            mPrinter = new Printer(Printer.TM_T88,Printer.LANG_EN,mContext);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "Printer", mContext);
            return false;
        }

        mPrinter.setReceiveEventListener(new ReceiveListener() {
            @Override
            public void onPtrReceive(Printer printer, final int code, final PrinterStatusInfo status, String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public synchronized void run() {
                        ShowMsg.showResult(code, makeErrorMessage(status), mContext);

                        dispPrinterWarnings(status);



                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                disconnectPrinter();
                            }
                        }).start();
                    }
                });
            }
        });

        return true;
    }

    private void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter.clearCommandBuffer();

        mPrinter.setReceiveEventListener(null);

        mPrinter = null;
    }

    private boolean printData() {
        if (mPrinter == null) {
            return false;
        }

        if (!connectPrinter()) {
            return false;
        }

        PrinterStatusInfo status = mPrinter.getStatus();

        dispPrinterWarnings(status);

        if (!isPrintable(status)) {
            ShowMsg.showMsg(makeErrorMessage(status), mContext);
            try {
                mPrinter.disconnect();
            }
            catch (Exception ex) {
                // Do nothing
            }
            return false;
        }

        try {
            mPrinter.sendData(Printer.PARAM_DEFAULT);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "sendData", mContext);
            try {
                mPrinter.disconnect();
            }
            catch (Exception ex) {
                // Do nothing
            }
            return false;
        }

        return true;
    }

    private boolean connectPrinter() {
        boolean isBeginTransaction = false;

        if (mPrinter == null) {
            return false;
        }

        try {
            mPrinter.connect(tgn, Printer.PARAM_DEFAULT);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "connect", mContext);
            return false;
        }

        try {
            mPrinter.beginTransaction();
            isBeginTransaction = true;
        }
        catch (Exception e) {
            ShowMsg.showException(e, "beginTransaction", mContext);
        }

        if (isBeginTransaction == false) {
            try {
                mPrinter.disconnect();
            }
            catch (Epos2Exception e) {
                // Do nothing
                return false;
            }
        }

        return true;
    }

    private void disconnectPrinter() {
        if (mPrinter == null) {
            return;
        }

        try {
            mPrinter.endTransaction();
        }
        catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public synchronized void run() {
                    ShowMsg.showException(e, "endTransaction", mContext);
                }
            });
        }

        try {
            mPrinter.disconnect();
        }
        catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public synchronized void run() {
                    ShowMsg.showException(e, "disconnect", mContext);
                }
            });
        }

        finalizeObject();
    }

    private boolean createReceiptData() {
        String method = "";
        StringBuilder textData = new StringBuilder();
        String aux = "TESTE STRING";

        if (mPrinter == null) {
            return false;
        }
        try {
            mPrinter.addTextAlign(Printer.ALIGN_CENTER);
            mPrinter.addFeedLine(1);
            mPrinter.addTextFont(Printer.FONT_B);
            mPrinter.addTextSize(2, 2);
            // Em Fonte A e tamanho 2 x 2 a linha suporta 21 caracteres
            // Em Fonte B e tamanho 2 x 2 a linha suporta 28 caracteres
            // Em Fonte A e tamanho 1 x 1 a linha suporta 42 caracteres
            textData.append("Cartão:   441\n");
            textData.append("CRISTIANO RONALDO\n\n");
            mPrinter.addText(textData.toString());
            textData.delete(0, textData.length());

            mPrinter.addTextFont(Printer.FONT_A);
            mPrinter.addTextSize(1, 1);
            mPrinter.addTextStyle(0,0,1,0);
            // em tamanho 1 x 1 a linha suporta 42 caracteres
            textData.append("-----------------------------\n");
            textData.append("1 x und  500 ML              \n");
            textData.append("CENOURA + BETERRABA + LARANJA\n");
            textData.append("SEM GELO                     \n");
            textData.append("C/ AÇUCAR E ADOÇANTE         \n");
            textData.append("-----------------------------\n");
            textData.append("2 x und  300 ML              \n");
            textData.append("LARANJA + CENOURA + COUVE    \n");
            textData.append("C/ GENGIBRE                  \n");
            textData.append("POUCO ADOÇANTE               \n");
            textData.append("-----------------------------\n");
            textData.append("1 x und  300 ML --> VIAGEM   \n");
            textData.append("MORANGO + LEITE              \n");
            textData.append("C/ BANANA                    \n");
            textData.append("C/ LEITE CONDENSADO          \n");
            textData.append("-----------------------------\n");
            mPrinter.addText(textData.toString());
            textData.delete(0, textData.length());

            mPrinter.addFeedLine(1);
            mPrinter.addCut(Printer.CUT_FEED);
        }
        catch (Exception e) {
            ShowMsg.showException(e, method, mContext);
            return false;
        }

        textData = null;

        return true;
    }

    private boolean runPrintReceiptSequence() {
        if (!initializeObject()) {
            return false;
        }

        if (!createReceiptData()) {
            finalizeObject();
            return false;
        }

        if (!printData()) {
            finalizeObject();
            return false;
        }

        return true;
    }


    private boolean isPrintable(PrinterStatusInfo status) {
        if (status == null) {
            return false;
        }

        if (status.getConnection() == Printer.FALSE) {
            return false;
        }
        else if (status.getOnline() == Printer.FALSE) {
            return false;
        }
        else {
            ;//print available
        }

        return true;
    }

    private String makeErrorMessage(PrinterStatusInfo status) {
        String msg = "";

        if (status.getOnline() == Printer.FALSE) {
            msg += getString(R.string.handlingmsg_err_offline);
        }
        if (status.getConnection() == Printer.FALSE) {
            msg += getString(R.string.handlingmsg_err_no_response);
        }
        if (status.getCoverOpen() == Printer.TRUE) {
            msg += getString(R.string.handlingmsg_err_cover_open);
        }
        if (status.getPaper() == Printer.PAPER_EMPTY) {
            msg += getString(R.string.handlingmsg_err_receipt_end);
        }
        if (status.getPaperFeed() == Printer.TRUE || status.getPanelSwitch() == Printer.SWITCH_ON) {
            msg += getString(R.string.handlingmsg_err_paper_feed);
        }
        if (status.getErrorStatus() == Printer.MECHANICAL_ERR || status.getErrorStatus() == Printer.AUTOCUTTER_ERR) {
            msg += getString(R.string.handlingmsg_err_autocutter);
            msg += getString(R.string.handlingmsg_err_need_recover);
        }
        if (status.getErrorStatus() == Printer.UNRECOVER_ERR) {
            msg += getString(R.string.handlingmsg_err_unrecover);
        }
        if (status.getErrorStatus() == Printer.AUTORECOVER_ERR) {
            if (status.getAutoRecoverError() == Printer.HEAD_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat);
                msg += getString(R.string.handlingmsg_err_head);
            }
            if (status.getAutoRecoverError() == Printer.MOTOR_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat);
                msg += getString(R.string.handlingmsg_err_motor);
            }
            if (status.getAutoRecoverError() == Printer.BATTERY_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat);
                msg += getString(R.string.handlingmsg_err_battery);
            }
            if (status.getAutoRecoverError() == Printer.WRONG_PAPER) {
                msg += getString(R.string.handlingmsg_err_wrong_paper);
            }
        }
        if (status.getBatteryLevel() == Printer.BATTERY_LEVEL_0) {
            msg += getString(R.string.handlingmsg_err_battery_real_end);
        }

        return msg;
    }

    private void dispPrinterWarnings(PrinterStatusInfo status) {
        EditText edtWarnings = findViewById(R.id.edtWarnings);
        String warningsMsg = "";

        if (status == null) {
            return;
        }

        if (status.getPaper() == Printer.PAPER_NEAR_END) {
            warningsMsg += getString(R.string.handlingmsg_warn_receipt_near_end);
        }

        edtWarnings.setText(warningsMsg);
    }


    private void requestRuntimePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        List<String> requestPermissions = new ArrayList<>();

        if (permissionStorage == PackageManager.PERMISSION_DENIED) {
            requestPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissionLocation == PackageManager.PERMISSION_DENIED) {
            requestPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (!requestPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, requestPermissions.toArray(new String[requestPermissions.size()]), REQUEST_PERMISSION);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode != REQUEST_PERMISSION || grantResults.length == 0) {
            return;
        }

        List<String> requestPermissions = new ArrayList<>();

        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                requestPermissions.add(permissions[i]);
            }
            if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)
                    && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                requestPermissions.add(permissions[i]);
            }
        }

        if (!requestPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, requestPermissions.toArray(new String[requestPermissions.size()]), REQUEST_PERMISSION);
        }
    }


}
