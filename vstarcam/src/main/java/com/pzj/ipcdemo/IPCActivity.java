package com.pzj.ipcdemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.mediatek.demo.smartconnection.MainActivity;
import com.pzj.ipcdemo.adapter.IPCListAdapter;
import com.pzj.ipcdemo.entity.VStarCamera;
import com.pzj.ipcdemo.service.BridgeService;
import com.pzj.ipcdemo.utils.ContentCommon;
import com.pzj.ipcdemo.utils.ImageDispose;
import com.pzj.ipcdemo.utils.JSONUtil;
import com.pzj.ipcdemo.utils.SPUtil;
import com.pzj.ipcdemo.utils.SystemUIUtil;
import com.pzj.ipcdemo.view.SearchAnyLayerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import per.goweii.anylayer.AnimHelper;
import per.goweii.anylayer.AnyLayer;
import vstc2.nativecaller.NativeCaller;

/**
 * 视频连接流程:
 * 1. 启动服务 BridgeService；
 * 2. 初始化服务器 NativeCaller. PPPPInitial
 * 3. 初始化回调 NativeCaller. Init ();
 * 4. 开启 p2p 连接 StartPPPP；
 * 5. p2p 返回在线之后 开启视频流(视频画面) StartPPPPLivestream
 * 6 关闭视频流 StopPPPPLivestream
 * 7 断开 p2p 连接 StopPPPP
 * 8 释放 p2p 资源 NativeCaller. Free ();
 */

public class IPCActivity extends AppCompatActivity implements BridgeService.IpcamClientInterface, View.OnClickListener ,SearchAnyLayerView.OnSelectSearchListener {

    public static String TAG = "IPCActivity";

    //SharedPreferences文件名
    public static final String SP_FILE_NAME = "share_ipc";
    //摄像头列表数据主键名
    public static final String SP_IPC_KEY = "vstarcam_devices";

    //设备搜索对话框
    private SearchAnyLayerView searchAnyLayerView;



    //设备列表
   private List<VStarCamera> vStarCameraList=new ArrayList<>();

   private IPCListAdapter ipcListAdapter;


    private RecyclerView rv_ipcList;
    private FloatingActionButton fb_addCamera;
    private AnyLayer editDialog;//手动添加设备对话框




    public static String deviceName = "admin";//设备账号
    public static String devicePass = "12345678";//设备密码
    public static String deviceId = "VSTA899484MXNCJ";//设备唯一标识

    public static final int PPPP_STATUS_CONNECTING = 0; // 连接中
    public static final int PPPP_STATUS_INITIALING = 1; // 已连接，正在初始化
    public static final int PPPP_STATUS_ON_LINE = 2; // 在线
    public static final int PPPP_STATUS_CONNECT_FAILED = 3; // 连接失败
    public static final int PPPP_STATUS_DISCONNECT = 4; // / 连接已关闭
    public static final int PPPP_STATUS_INVALID_ID = 5; // 无效 UID
    public static final int PPPP_STATUS_DEVICE_NOT_ON_LINE = 6; // 不在线
    public static final int PPPP_STATUS_CONNECT_TIMEOUT = 7; // 连接超时
    public static final int PPPP_STATUS_WRONGUSER_RIGHTPWD = 8; // 密码错误 ..
    public static final int PPPP_STATUS_WRONGPWD_RIGHTUSER = 9; // 密码错误. .
    public static final int PPPP_STATUS_WRONGPWD_WRONGUSER = 10; // 密码错误. .


    //扫码回传数据主键名称
    private static final String DECODED_CONTENT_KEY = "codedContent";
    private static final String DECODED_BITMAP_KEY = "codedBitmap";
    private static final int REQUEST_CODE_SCAN = 0x0000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ipc);
        //隐藏状态栏导航栏
        SystemUIUtil.setSystemUIVisible(this, false);
        Toolbar toolbar = (Toolbar) findViewById(R.id.tb_ipc);
        toolbar.setTitleTextColor(getResources().getColor(R.color.tab_bgcolor));
        toolbar.setTitle("Camera");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //设置4.4及以上的状态栏上内边距
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            toolbar.setPadding(0, getStatusBarHeight(this), 0, 0);
        }
        //获取窗口对象
        Window window = this.getWindow();
        //设置透明状态栏,使 ContentView 内容覆盖状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //需要设置这个 flag 才能调用 setStatusBarColor 来设置状态栏颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        //初始化控件
        initView();


        //获取设备数据
        String ipcJsonStr=SPUtil.getString(this,SP_FILE_NAME,SP_IPC_KEY,"");
        //将Json字符串转成实体对象
        vStarCameraList=JSONUtil.jsonToList(ipcJsonStr,VStarCamera.class);
        if(vStarCameraList==null){
            vStarCameraList=new ArrayList<>();
        }

        Log.d(TAG,"获取设备数据:"+vStarCameraList.size()+"   JsonStr:"+ipcJsonStr);

        ipcListAdapter=new IPCListAdapter(this,vStarCameraList);
        rv_ipcList.setAdapter(ipcListAdapter);

        //子控件点击事件
        ipcListAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {


                //设备配置设置
                if (view.getId()==R.id.iv_ipcSetting){
                    //如果设备在线
                    if ( ipcListAdapter.getIpcStates().get(position)==PPPP_STATUS_ON_LINE) {
                        VStarCamera vStarCamera = vStarCameraList.get(position);
                        //跳转到设置页面
                        Intent intent=new Intent("com.shima.smartbushome.vstarcam.settings");
                        intent.putExtra(ContentCommon.STR_CAMERA_ID,vStarCamera.getId());
                        intent.putExtra(ContentCommon.STR_CAMERA_NAME,vStarCamera.getUsername());
                        intent.putExtra(ContentCommon.STR_CAMERA_PWD,vStarCamera.getPassword());
                        startActivity(intent);
                    }

                }

                //设备信息修改
                if (view.getId()==R.id.iv_ipcUpdate){
                    //显示修改摄像头对话框
                    showUpdateCameraEditDialog(vStarCameraList.get(position));
                }

                //删除IPC
                if (view.getId()==R.id.iv_ipcDelete){
                    vStarCameraList.remove(position);
                    ipcListAdapter.getIpcStates().remove(position);
                    //保存删除后的数据
                    String str=JSONUtil.listToJson(vStarCameraList);
                    SPUtil.put(IPCActivity.this,SP_FILE_NAME,SP_IPC_KEY,str);
                    //刷新
                    ipcListRefreshData();
                }

                //视屏画面
                if (view.getId()==R.id.iv_item){
                    //如果设备在线
                    if ( ipcListAdapter.getIpcStates().get(position)==PPPP_STATUS_ON_LINE){
                        VStarCamera vStarCamera=vStarCameraList.get(position);

                        //跳转到实时视屏画面
                        Intent intent=new Intent("com.pzj.play");
                        Bundle bd=new Bundle();
                        bd.putString("name",vStarCamera.getUsername());
                        bd.putString("pass",vStarCamera.getPassword());
                        bd.putString("id",vStarCamera.getId());
                        intent.putExtras(bd);
                        IPCActivity.this.startActivity(intent);
                    }
                }
            }
        });
        //子控件长按事件
        ipcListAdapter.setOnItemChildLongClickListener(new BaseQuickAdapter.OnItemChildLongClickListener() {
            @Override
            public boolean onItemChildLongClick(BaseQuickAdapter adapter, View view, int position) {
                LinearLayout layout= (LinearLayout) ipcListAdapter.getViewByPosition(rv_ipcList,position,R.id.layout_settings);
                //长按显示或者取消菜单
                if (view.getId()==R.id.iv_item){
                    switch (layout.getVisibility()){
                        case View.VISIBLE:
                            layout.setVisibility(View.INVISIBLE);
                            break;
                        case View.INVISIBLE:
                            layout.setVisibility(View.VISIBLE);
                            break;
                        default:
                            layout.setVisibility(View.INVISIBLE);
                    }
                }
                //长按取消菜单
                if (view.getId()==R.id.iv_ipcSetting|view.getId()==R.id.iv_ipcDelete|view.getId()==R.id.iv_ipcUpdate){
                    layout.setVisibility(View.INVISIBLE);
                }


                return true;
            }
        });








        //启动服务
        Intent intent = new Intent();
        intent.setClass(IPCActivity.this, BridgeService.class);
        startService(intent);
        Log.d(TAG,"启动服务....");

        //准备摄像头初始化连接工作 线程
        new IpcInitThread().start();
        //开启线程 准备连接工作
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//
//                    //初始化服务器
//                    NativeCaller.PPPPInitialOther("ADCBBFAOPPJAHGJGBBGLFLAGDBJJHNJGGMBFBKHIBBNKOKLDHOBHCBOEHOKJJJKJBPMFLGCPPJMJAPDOIPNL");
//                    Log.d(TAG,"初始化服务器...");
//                    Thread.sleep(3000);
//                    // NativeCaller.SetAPPDataPath(getApplicationContext().getFilesDir().getAbsolutePath());
//                    //初始化回调
//                    NativeCaller.Init();
//                    Log.d(TAG,"初始化回调...");
//                    Thread.sleep(200);
//                    //开启 p2p 连接 (账号，密码，设备ID)
//                    startCameraPPPP(deviceName,devicePass,deviceId);
//                    Log.d(TAG,"开启 p2p 连接 (账号:"+deviceName+"，密码:"+devicePass+"，设备ID:"+deviceId);
//                } catch (Exception e) {
//
//                }
//            }
//        }).start();

        //绑定回调
        BridgeService.setIpcamClientInterface(this);

        //绑定事件
        fb_addCamera.setOnClickListener(this);
    }


    Handler ipcStatusHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle bundle=msg.getData();
            String did=bundle.getString("did");
            switch (msg.what){
                case 1://设备状态信息
                    int type=bundle.getInt("type");
                    int param=bundle.getInt("param");

                    //刷新设备连接状态状态
                    for (int i=0;i<vStarCameraList.size();i++){
                        VStarCamera vStarCamera=vStarCameraList.get(i);
                        if (did.equals(vStarCamera.getId())){
                            ipcListAdapter.setIpcStatesIndex(param,i);
                        }
                    }
                    break;
                case 2://设备预览图
                    byte[] bImage=bundle.getByteArray("bImage");
                    int len=bundle.getInt("len");

                    //将字节数组转换为ImageView可调用的Bitmap对象
                   // Bitmap bitmap=ImageDispose.getPicFromBytes(bImage,new BitmapFactory.Options());

                    //刷新设备列表
                    for (int i=0;i<vStarCameraList.size();i++){
                        if (did.equals(vStarCameraList.get(i).getId())){
                            vStarCameraList.get(i).setbImage(bImage);
                            ipcListAdapter.notifyDataSetChanged();
                        }
                    }

                    //保存预览图数据
                    String str=JSONUtil.listToJson(vStarCameraList);
                    SPUtil.put(IPCActivity.this,SP_FILE_NAME,SP_IPC_KEY,str);

                    break;
            }





//            switch (states){
//                    case PPPP_STATUS_CONNECTING : // 连接中
//                        button.setText("连接中");
//                        break;
//                    case PPPP_STATUS_INITIALING : // 已连接，正在初始化
//                        button.setText("正在初始化");
//                        break;
//                    case PPPP_STATUS_ON_LINE : // 在线
//                        button.setText("在线");
//                        break;
//                    case PPPP_STATUS_CONNECT_FAILED : // 连接失败
//                        button.setText("连接失败");
//                        break;
//                    case PPPP_STATUS_DISCONNECT : // / 连接已关闭
//                        button.setText("连接已关闭");
//                        break;
//                    case PPPP_STATUS_INVALID_ID :// 无效 UID
//                        button.setText("无效 UID");
//                        break;
//                    case PPPP_STATUS_DEVICE_NOT_ON_LINE : // 不在线
//                        button.setText("不在线");
//                        break;
//                    case PPPP_STATUS_CONNECT_TIMEOUT : // 连接超时
//                        button.setText("连接超时");
//                        break;
//                    case PPPP_STATUS_WRONGUSER_RIGHTPWD : // 密码错误 ..
//                    case PPPP_STATUS_WRONGPWD_RIGHTUSER : // 密码错误. .
//                    case PPPP_STATUS_WRONGPWD_WRONGUSER : // 密码错误. .
//                        button.setText("密码错误");
//                        break;
//
//                }

        }
    };

    //搜索设备点击回调
    @Override
    public void onSelectSearchListener(VStarCamera vStarCamera) {
        //查看是否已经添加
        for (VStarCamera item: vStarCameraList){
            if (item.getId().equals(vStarCamera.getId())){
                //关闭搜索对话框
                Toast.makeText(this, "Can't add devices repeatedly", Toast.LENGTH_SHORT).show();
                searchAnyLayerView.dismiss();
                return;
            }
        }

        searchAnyLayerView.dismiss();
        //显示手动添加摄像头
        showAddCameraEditDialog(vStarCamera);
    }


    //准备摄像头初始化连接工作 线程
    class IpcInitThread extends Thread{
        @Override
        public void run() {
            super.run();
            try {



                for (VStarCamera item: vStarCameraList) {
                    //初始化服务器
                    NativeCaller.PPPPInitialOther("ADCBBFAOPPJAHGJGBBGLFLAGDBJJHNJGGMBFBKHIBBNKOKLDHOBHCBOEHOKJJJKJBPMFLGCPPJMJAPDOIPNL");
                    Log.d(TAG,"初始化服务器...");
                    Thread.sleep(3000);
                    // NativeCaller.SetAPPDataPath(getApplicationContext().getFilesDir().getAbsolutePath());
                    //初始化回调
                    NativeCaller.Init();
                    Log.d(TAG,"初始化回调...");
                    Thread.sleep(200);
                    //开启 p2p 连接 (账号，密码，设备ID)
                    startCameraPPPP(item.getUsername(),item.getPassword(),item.getId());
                    Log.d(TAG,"开启 p2p 连接 (账号:"+item.getUsername()+"，密码:"+item.getPassword()+"，设备ID:"+item.getId());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    public void initView() {
        rv_ipcList= (RecyclerView) this.findViewById(R.id.rv_ipcList);
        fb_addCamera = (FloatingActionButton) this.findViewById(R.id.fb_addCamera);

        //初始化列表布局
        RecyclerView.LayoutManager layout = new GridLayoutManager(this, 1);//网格布局，每行为1
        rv_ipcList.setLayoutManager(layout);
        rv_ipcList.setHasFixedSize(true);//适配器内容改变，不会改变RecyclerView的大小
    }

    //刷新摄像头列表
    public void ipcListRefreshData(){
        vStarCameraList.clear();
        String ipcJsonStr=SPUtil.getString(this,SP_FILE_NAME,SP_IPC_KEY,"");
        List<VStarCamera> vStarCameras=JSONUtil.jsonToList(ipcJsonStr,VStarCamera.class);
        vStarCameraList.addAll(vStarCameras);
        ipcListAdapter.notifyDataSetChanged();
    }

    //获取状态栏高度
    public int getStatusBarHeight(Context context) {
        int statusBarHeight = 0;

        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    //页面菜单点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.finish();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 开启p2p连接
     *
     * @param deviceName//账号
     * @param devicePass//密码
     * @param deviceId//设备ID
     */
    private void startCameraPPPP(String deviceName, String devicePass, String deviceId) {

        if (deviceId.toLowerCase().startsWith("vsta")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "EFGFFBBOKAIEGHJAEDHJFEEOHMNGDCNJCDFKAKHLEBJHKEKMCAFCDLLLHAOCJPPMBHMNOMCJKGJEBGGHJHIOMFBDNPKNFEGCEGCBGCALMFOHBCGMFK", 0);
        } else if (deviceId.toLowerCase().startsWith("vstd")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "HZLXSXIALKHYEIEJHUASLMHWEESUEKAUIHPHSWAOSTEMENSQPDLRLNPAPEPGEPERIBLQLKHXELEHHULOEGIAEEHYEIEK-$$", 1);
        } else if (deviceId.toLowerCase().startsWith("vstf")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "HZLXEJIALKHYATPCHULNSVLMEELSHWIHPFIBAOHXIDICSQEHENEKPAARSTELERPDLNEPLKEILPHUHXHZEJEEEHEGEM-$$", 1);
        } else if (deviceId.toLowerCase().startsWith("vste")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "EEGDFHBAKKIOGNJHEGHMFEEDGLNOHJMPHAFPBEDLADILKEKPDLBDDNPOHKKCIFKJBNNNKLCPPPNDBFDL", 0);
        } else if (deviceId.toLowerCase().startsWith("pisr")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "EFGFFBBOKAIEGHJAEDHJFEEOHMNGDCNJCDFKAKHLEBJHKEKMCAFCDLLLHAOCJPPMBHMNOMCJKGJEBGGHJHIOMFBDNPKNFEGCEGCBGCALMFOHBCGMFK", 0);
        } else if (deviceId.toLowerCase().startsWith("vstg")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "EEGDFHBOKCIGGFJPECHIFNEBGJNLHOMIHEFJBADPAGJELNKJDKANCBPJGHLAIALAADMDKPDGOENEBECCIK:vstarcam2018", 0);
        } else if (deviceId.toLowerCase().startsWith("vsth")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "EEGDFHBLKGJIGEJLEKGOFMEDHAMHHJNAGGFABMCOBGJOLHLJDFAFCPPHGILKIKLMANNHKEDKOINIBNCPJOMK:vstarcam2018", 0);
        } else if (deviceId.toLowerCase().startsWith("vstb") || deviceId.toLowerCase().startsWith("vstc")) {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "ADCBBFAOPPJAHGJGBBGLFLAGDBJJHNJGGMBFBKHIBBNKOKLDHOBHCBOEHOKJJJKJBPMFLGCPPJMJAPDOIPNL", 0);
        } else {
            NativeCaller.StartPPPPExt(deviceId, deviceName,
                    devicePass, 1, "", "", 0);
        }
    }

    /**
     * 断开p2p连接
     *
     * @param deviceId//设备ID
     */
    private void stopCameraPPPP(String deviceId) {
        NativeCaller.StopPPPP(deviceId);
    }


    //设备状态信息及视频连接流程相关参数回调
    @Override
    public void BSMsgNotifyData(String did, int type, int param) {
        Log.d(TAG, "设备状态信息及视频连接流程相关参数回调");
        Log.d(TAG, "did:" + did);
        Log.d(TAG, "type:" + type);
        Log.d(TAG, "param:" + param);

        //通知到handler 刷新
        Message message= new Message();
        message.what=1;
        Bundle bundle =new Bundle();
        bundle.putString("did",did);
        bundle.putInt("type",type);
        bundle.putInt("param",param);
        message.setData(bundle);
        ipcStatusHandler.sendMessage(message);

//       final int states=param;
//        this.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                switch (states){
//                    case PPPP_STATUS_CONNECTING : // 连接中
//                        button.setText("连接中");
//                        break;
//                    case PPPP_STATUS_INITIALING : // 已连接，正在初始化
//                        button.setText("正在初始化");
//                        break;
//                    case PPPP_STATUS_ON_LINE : // 在线
//                        button.setText("在线");
//                        break;
//                    case PPPP_STATUS_CONNECT_FAILED : // 连接失败
//                        button.setText("连接失败");
//                        break;
//                    case PPPP_STATUS_DISCONNECT : // / 连接已关闭
//                        button.setText("连接已关闭");
//                        break;
//                    case PPPP_STATUS_INVALID_ID :// 无效 UID
//                        button.setText("无效 UID");
//                        break;
//                    case PPPP_STATUS_DEVICE_NOT_ON_LINE : // 不在线
//                        button.setText("不在线");
//                        break;
//                    case PPPP_STATUS_CONNECT_TIMEOUT : // 连接超时
//                        button.setText("连接超时");
//                        break;
//                    case PPPP_STATUS_WRONGUSER_RIGHTPWD : // 密码错误 ..
//                    case PPPP_STATUS_WRONGPWD_RIGHTUSER : // 密码错误. .
//                    case PPPP_STATUS_WRONGPWD_WRONGUSER : // 密码错误. .
//                        button.setText("密码错误");
//                        break;
//
//                }
//            }
//        });

    }

    /**
     * 返回预览图
     *
     * @param did
     * @param bImage
     * @param len
     */
    @Override
    public void BSSnapshotNotify(String did, byte[] bImage, int len) {
        Log.i(TAG, "BSSnapshotNotify---len" + len);


        //通知到handler 刷新
        Message message= new Message();
        message.what=2;
        Bundle bundle =new Bundle();
        bundle.putString("did",did);
        bundle.putByteArray("bImage",bImage);
        bundle.putInt("len",len);
        message.setData(bundle);
        ipcStatusHandler.sendMessage(message);

//        //将字节数组转换为ImageView可调用的Bitmap对象
//       final Bitmap bitmap=ImageDispose.getPicFromBytes(bImage,new BitmapFactory.Options());
//
//        this.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                iv_bimage.setImageBitmap(bitmap);
//            }
//        });
    }

    @Override
    public void callBackUserParams(String did, String user1, String pwd1, String user2, String pwd2, String user3, String pwd3) {

    }

    @Override
    public void CameraStatus(String did, int status) {

    }


    @Override
    protected void onResume() {
        super.onResume();
        //隐藏状态栏导航栏
        SystemUIUtil.setSystemUIVisible(this, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //释放资源
        try {
            for (VStarCamera item:vStarCameraList){
                NativeCaller.StopPPPPLivestream(item.getId());
                NativeCaller.StopPPPP(item.getId());
            }
            NativeCaller.Free();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.fb_addCamera) {
            showAddCameraTypeDialog();
        }
    }

    //显示添加摄像头方式对话框
    public void showAddCameraTypeDialog() {
        AnyLayer.with(this)
                .contentView(R.layout.dialog_add_camera_type)
                .backgroundBlurPercent(0.015F)//背景高斯模糊
                .gravity(Gravity.TOP | Gravity.CENTER)
                .cancelableOnTouchOutside(true)
                .cancelableOnClickKeyBack(true)
                .onClick(R.id.iv_link, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //跳转到配网界面
                        startActivity(new Intent(IPCActivity.this,MainActivity.class));
                    }
                })
                .onClick(R.id.iv_scan, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //扫码添加

                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }

                        //动态权限申请
                        if (ContextCompat.checkSelfPermission(IPCActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(IPCActivity.this, new String[]{Manifest.permission.CAMERA}, 1);
                        } else {
                            //跳转到扫码页面
                            goScan();
                        }
                    }
                })
                .onClick(R.id.iv_search, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //搜索添加

                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }


                        //设备搜索对话框
                        searchAnyLayerView=new SearchAnyLayerView(IPCActivity.this);
                        //绑定回调
                        searchAnyLayerView.setOnSelectSearchListener(IPCActivity.this);
                        //显示
                        searchAnyLayerView.show();
                    }
                })
                .onClick(R.id.iv_edit, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {

                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }
                        //手动添加0
                        showAddCameraEditDialog();

                    }
                })
                .contentAnim(new AnyLayer.IAnim() {
                    @Override
                    public long inAnim(View content) {
                        AnimHelper.startTopAlphaInAnim(content, 350);//设置进入动画
                        return 350;
                    }

                    @Override
                    public long outAnim(View content) {
                        AnimHelper.startTopAlphaOutAnim(content, 350);//设置退出动画
                        return 0;
                    }
                })
                .show();
    }


    //显示手动添加摄像头对话框
    public void showAddCameraEditDialog() {

        editDialog = AnyLayer.with(this)
                .contentView(R.layout.dialog_add_camera_edit)
                .backgroundBlurPercent(0.015F)//背景高斯模糊
                .gravity(Gravity.TOP | Gravity.CENTER)
                .cancelableOnTouchOutside(true)
                .cancelableOnClickKeyBack(true)
                .onClick(R.id.fl_dialog_no, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //取消
                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }
                    }
                })

                .contentAnim(new AnyLayer.IAnim() {
                    @Override
                    public long inAnim(View content) {
                        AnimHelper.startTopAlphaInAnim(content, 350);//设置进入动画
                        return 350;
                    }

                    @Override
                    public long outAnim(View content) {
                        AnimHelper.startTopAlphaOutAnim(content, 350);//设置退出动画
                        return 350;
                    }
                });

        final EditText et_name = editDialog.getView(R.id.et_name);
        final EditText et_id = editDialog.getView(R.id.et_id);
        final EditText et_username = editDialog.getView(R.id.et_username);
        final EditText et_password = editDialog.getView(R.id.et_password);
        FrameLayout fl_dialog_yes = editDialog.getView(R.id.fl_dialog_yes);

        fl_dialog_yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //确认添加
                String name=et_name.getText().toString();
                String id=et_id.getText().toString().trim();
                String username=et_username.getText().toString().trim();
                String password=et_password.getText().toString().trim();

                if (name.equals("")|id.equals("")|username.equals("")|password.equals("")){
                    Toast.makeText(IPCActivity.this, "Input cannot be empty", Toast.LENGTH_LONG).show();
                    return;
                }

                //将IPC实体对象集合转成Json字符串
                VStarCamera vStarCamera=new VStarCamera(name,id,username,password);
                vStarCameraList.add(vStarCamera);
                ipcListAdapter.getIpcStates().add(-1);//设备状态
                String str=JSONUtil.listToJson(vStarCameraList);

                //保存数据
                SPUtil.put(IPCActivity.this,SP_FILE_NAME,SP_IPC_KEY,str);

                //刷新列表
                ipcListRefreshData();

                if (editDialog.isShow()){
                    editDialog.dismiss();
                }

                //释放资源
                try {
                    for (VStarCamera item:vStarCameraList){
                        NativeCaller.StopPPPPLivestream(item.getId());
                        NativeCaller.StopPPPP(item.getId());
                    }
                    NativeCaller.Free();
                }catch (Exception e){
                    e.printStackTrace();
                }
                //准备摄像头初始化连接工作 线程
                new IpcInitThread().start();

            }
        });

        editDialog.show();
    }


    //显示手动添加摄像头对话框
    public void showAddCameraEditDialog(VStarCamera vStarCamera) {

        editDialog = AnyLayer.with(this)
                .contentView(R.layout.dialog_add_camera_edit)
                .backgroundBlurPercent(0.015F)//背景高斯模糊
                .gravity(Gravity.TOP | Gravity.CENTER)
                .cancelableOnTouchOutside(true)
                .cancelableOnClickKeyBack(true)
                .onClick(R.id.fl_dialog_no, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //取消
                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }
                    }
                })

                .contentAnim(new AnyLayer.IAnim() {
                    @Override
                    public long inAnim(View content) {
                        AnimHelper.startTopAlphaInAnim(content, 350);//设置进入动画
                        return 350;
                    }

                    @Override
                    public long outAnim(View content) {
                        AnimHelper.startTopAlphaOutAnim(content, 350);//设置退出动画
                        return 350;
                    }
                });

        final EditText et_name = editDialog.getView(R.id.et_name);
        final EditText et_id = editDialog.getView(R.id.et_id);
        final EditText et_username = editDialog.getView(R.id.et_username);
        final EditText et_password = editDialog.getView(R.id.et_password);
        FrameLayout fl_dialog_yes = editDialog.getView(R.id.fl_dialog_yes);

        et_name.setText(""+vStarCamera.getName());
        et_id.setText(""+vStarCamera.getId());
        et_username.setText(""+vStarCamera.getUsername());
        et_password.setText(""+vStarCamera.getPassword());

        fl_dialog_yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //确认添加
                String name=et_name.getText().toString();
                String id=et_id.getText().toString().trim();
                String username=et_username.getText().toString().trim();
                String password=et_password.getText().toString().trim();

                if (name.equals("")|id.equals("")|username.equals("")|password.equals("")){
                    Toast.makeText(IPCActivity.this, "Input cannot be empty", Toast.LENGTH_LONG).show();
                    return;
                }

                //将IPC实体对象集合转成Json字符串
                VStarCamera vStarCamera=new VStarCamera(name,id,username,password);
                vStarCameraList.add(vStarCamera);
                ipcListAdapter.getIpcStates().add(-1);//设备状态
                String str=JSONUtil.listToJson(vStarCameraList);

                //保存数据
                SPUtil.put(IPCActivity.this,SP_FILE_NAME,SP_IPC_KEY,str);

                //刷新列表
                ipcListRefreshData();

                if (editDialog.isShow()){
                    editDialog.dismiss();
                }

                //释放资源
                try {
                    for (VStarCamera item:vStarCameraList){
                        NativeCaller.StopPPPPLivestream(item.getId());
                        NativeCaller.StopPPPP(item.getId());
                    }
                    NativeCaller.Free();
                }catch (Exception e){
                    e.printStackTrace();
                }

                //准备摄像头初始化连接工作 线程
                new IpcInitThread().start();

            }
        });

        editDialog.show();
    }


    //显示修改摄像头对话框
    public void showUpdateCameraEditDialog(VStarCamera vStarCamera) {

        final VStarCamera updateVStarCamera=vStarCamera;

        editDialog = AnyLayer.with(this)
                .contentView(R.layout.dialog_add_camera_edit)
                .backgroundBlurPercent(0.015F)//背景高斯模糊
                .gravity(Gravity.TOP | Gravity.CENTER)
                .cancelableOnTouchOutside(true)
                .cancelableOnClickKeyBack(true)
                .onClick(R.id.fl_dialog_no, new AnyLayer.OnLayerClickListener() {
                    @Override
                    public void onClick(AnyLayer anyLayer, View v) {
                        //取消
                        if (anyLayer.isShow()) {
                            anyLayer.dismiss();
                        }
                    }
                })

                .contentAnim(new AnyLayer.IAnim() {
                    @Override
                    public long inAnim(View content) {
                        AnimHelper.startTopAlphaInAnim(content, 350);//设置进入动画
                        return 350;
                    }

                    @Override
                    public long outAnim(View content) {
                        AnimHelper.startTopAlphaOutAnim(content, 350);//设置退出动画
                        return 350;
                    }
                });

        final TextView tv_title = editDialog.getView(R.id.tv_title);
        final EditText et_name = editDialog.getView(R.id.et_name);
        final EditText et_id = editDialog.getView(R.id.et_id);
        final EditText et_username = editDialog.getView(R.id.et_username);
        final EditText et_password = editDialog.getView(R.id.et_password);
        FrameLayout fl_dialog_yes = editDialog.getView(R.id.fl_dialog_yes);

        tv_title.setText("Update Camera");
        et_name.setText(""+vStarCamera.getName());
        et_id.setHint(""+vStarCamera.getId());
        et_id.setFocusableInTouchMode(false);//不可编辑
        et_username.setText(""+vStarCamera.getUsername());
        et_password.setText(""+vStarCamera.getPassword());

        fl_dialog_yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //确认修改
                String name=et_name.getText().toString();
                String id=updateVStarCamera.getId();
                String username=et_username.getText().toString().trim();
                String password=et_password.getText().toString().trim();

                if (name.equals("")|username.equals("")|password.equals("")){
                    Toast.makeText(IPCActivity.this, "Input cannot be empty", Toast.LENGTH_LONG).show();
                    return;
                }

                //修改设备连接数据
                for (int i=0;i<vStarCameraList.size();i++){
                    if (vStarCameraList.get(i).getId().equals(id)){
                        vStarCameraList.get(i).setName(name);
                        vStarCameraList.get(i).setUsername(username);
                        vStarCameraList.get(i).setPassword(password);
                    }
                }

                //将IPC实体对象集合转成Json字符串
                String str=JSONUtil.listToJson(vStarCameraList);

                //保存数据
                SPUtil.put(IPCActivity.this,SP_FILE_NAME,SP_IPC_KEY,str);

                //刷新列表
                ipcListRefreshData();

                if (editDialog.isShow()){
                    editDialog.dismiss();
                }

                //释放资源
                try {
                    for (VStarCamera item:vStarCameraList){
                        NativeCaller.StopPPPPLivestream(item.getId());
                        NativeCaller.StopPPPP(item.getId());
                    }
                    NativeCaller.Free();
                }catch (Exception e){
                    e.printStackTrace();
                }
                //准备摄像头初始化连接工作 线程
                new IpcInitThread().start();

            }
        });

        editDialog.show();
    }


    /**
     * 跳转到扫码界面扫码
     */
    private void goScan(){
        Intent intent = new Intent("com.shima.smartbushome.scan");
        startActivityForResult(intent, REQUEST_CODE_SCAN);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    goScan();
                } else {
                    Toast.makeText(this, "You refused to apply for permission. You may not be able to open the camera scanner!", Toast.LENGTH_SHORT).show();
                    //Toast.makeText(this, "你拒绝了权限申请，可能无法打开相机扫码哟！", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 扫描二维码/条码回传
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK) {
            if (data != null) {
                //返回的文本内容
                String content = data.getStringExtra(DECODED_CONTENT_KEY);
                //返回的BitMap图像
                //Bitmap bitmap = data.getParcelableExtra(DECODED_BITMAP_KEY);

                //扫码内容格式：{"ACT":"Add","ID":"VSTA899484MXNCJ","DT":"C38","WiFi":"0"}
                Log.d(TAG,"扫码内容是：" + content);

                //解析二维码内容获取摄像头ID
                String cameraID=getCameraIDByJsonStr(content);

                if (cameraID.length()==0){
                    Toast.makeText(this, "Error: Invalid content", Toast.LENGTH_SHORT).show();
                    return;
                }

                //查看是否已经添加
                for (VStarCamera item: vStarCameraList){
                    if (item.getId().equals(cameraID)){
                        //关闭搜索对话框
                        Toast.makeText(this, "Can't add devices repeatedly", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

               VStarCamera vStarCamera=new VStarCamera("IPCAM",cameraID,"admin","");

                //显示手动添加摄像头
                showAddCameraEditDialog(vStarCamera);

            }
        }
    }



    //解析二维码内容获取摄像头ID  {"ACT":"Add","ID":"VSTA899484MXNCJ","DT":"C38","WiFi":"0"}
    public String getCameraIDByJsonStr(String jsonStr){
        String cameraID="";
        try{
            JSONObject jsonObject = new JSONObject(jsonStr);
            /**
             * 为什么要使用jsonObject.optString， 不使用jsonObject.getString
             * 因为jsonObject.optString获取null不会报错
             */

            cameraID = jsonObject.optString("ID", null);

            // 日志打印结果：
            Log.d(TAG, "解析二维码内容获取摄像头ID：" + cameraID) ;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return cameraID;
    }


}
