package vn.edu.vhu.ltdd.a7permission;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "A7_241A010725";
    private static final String CHANNEL_ID = "a7_channel";
    private static final int NOTI_ID = 1001;

    private ImageView imgAnh;
    private TextView tvTrangThai, tvLienHe;
    private String thongTinAnh = "";   // giữ dòng kích thước ảnh, không bị onResume ghi đè

    // ---------- CAMERA ----------
    private final ActivityResultLauncher<String> xinQuyenCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền CAMERA: " + duocCap);
                if (duocCap) {
                    moCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    hienGiaiThich();
                } else {
                    hienMoCaiDat(R.string.blocked_camera);
                }
            });

    // ---------- POST_NOTIFICATIONS ----------
    private final ActivityResultLauncher<String> xinQuyenThongBao = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) guiThongBao();
                else Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
            });

    // ---------- CHỤP ẢNH XEM TRƯỚC ----------
    private final ActivityResultLauncher<Void> chupAnh = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            (Bitmap bitmap) -> {
                if (bitmap != null) {
                    imgAnh.setImageBitmap(bitmap);
                    thongTinAnh = getString(R.string.status_photo,
                            bitmap.getWidth(), bitmap.getHeight());
                    capNhatTrangThai();
                } else {
                    Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    // ---------- NC1: XIN HAI QUYỀN CÙNG LÚC ----------
    private final ActivityResultLauncher<String[]> xinNhieuQuyen = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> kq) -> {
                Log.d(TAG, "Kết quả xin nhiều quyền: " + kq);

                // Xử lý riêng quyền CAMERA
                boolean cam = Boolean.TRUE.equals(kq.get(Manifest.permission.CAMERA));
                if (cam) {
                    Toast.makeText(this, R.string.camera_granted_toast, Toast.LENGTH_SHORT).show();
                } else if (kq.containsKey(Manifest.permission.CAMERA)) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Toast.makeText(this, R.string.camera_denied_toast, Toast.LENGTH_SHORT).show();
                    } else {
                        hienMoCaiDat(R.string.blocked_camera);
                    }
                }

                // Xử lý riêng quyền thông báo (null nếu máy < Android 13)
                Boolean tb = kq.get(Manifest.permission.POST_NOTIFICATIONS);
                if (tb != null) {
                    if (tb) guiThongBao();
                    else Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
                }
                capNhatTrangThai();
            });

    // ---------- NC2: READ_CONTACTS ----------
    private final ActivityResultLauncher<String> xinQuyenLienHe = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    docLienHe();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    Toast.makeText(this, R.string.contacts_denied, Toast.LENGTH_SHORT).show();
                } else {
                    hienMoCaiDat(R.string.blocked_contacts);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        imgAnh = findViewById(R.id.imgAnh);
        tvTrangThai = findViewById(R.id.tvTrangThai);
        tvLienHe = findViewById(R.id.tvLienHe);
        Button btnChupAnh = findViewById(R.id.btnChupAnh);
        Button btnThongBao = findViewById(R.id.btnThongBao);
        Button btnNhieu = findViewById(R.id.btnNhieu);
        Button btnLienHe = findViewById(R.id.btnLienHe);
        Button btnCaiDat = findViewById(R.id.btnCaiDat);

        taoKenhThongBao();

        btnChupAnh.setOnClickListener(v -> kiemTraRoiChup());
        btnThongBao.setOnClickListener(v -> kiemTraRoiGuiThongBao());
        btnNhieu.setOnClickListener(v -> xinCaHaiQuyen());
        btnLienHe.setOnClickListener(v -> kiemTraRoiDocLienHe());
        btnCaiDat.setOnClickListener(v -> moCaiDatUngDung());
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatTrangThai();
    }

    // ===================== CAMERA =====================
    private void kiemTraRoiChup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            moCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            hienGiaiThich();
        } else {
            xinQuyenCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void hienGiaiThich() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_camera)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenCamera.launch(Manifest.permission.CAMERA))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Dùng chung cho mọi quyền bị chặn vĩnh viễn: chỉ khác nội dung thông báo. */
    private void hienMoCaiDat(int messageRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moCamera() {
        chupAnh.launch(null);
    }

    private void moCaiDatUngDung() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    // ===================== NC1 =====================
    private void xinCaHaiQuyen() {
        String[] ds = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.CAMERA};
        xinNhieuQuyen.launch(ds);
    }

    // ===================== NC2 =====================
    private void kiemTraRoiDocLienHe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            docLienHe();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rationale_title)
                    .setMessage(R.string.rationale_contacts)
                    .setPositiveButton(R.string.agree,
                            (d, w) -> xinQuyenLienHe.launch(Manifest.permission.READ_CONTACTS))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            xinQuyenLienHe.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void docLienHe() {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null, null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")) {
            int n = 0;
            while (c != null && c.moveToNext() && n < 5) {
                sb.append(++n).append(". ").append(c.getString(0)).append("\n");
            }
        }
        tvLienHe.setText(sb.length() == 0 ? getString(R.string.contacts_empty) : sb.toString());
    }

    // ===================== THÔNG BÁO =====================
    private void kiemTraRoiGuiThongBao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            guiThongBao();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            guiThongBao();
        } else {
            xinQuyenThongBao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void taoKenhThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kenh = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            kenh.setDescription(getString(R.string.channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(kenh);
        }
    }

    /** NC3: bấm vào thông báo thì mở lại app. */
    private void guiThongBao() {
        Intent mo = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mo, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.noti_title))
                .setContentText(getString(R.string.noti_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(this).notify(NOTI_ID, b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Thiếu quyền gửi thông báo", e);
        }
    }

    // ===================== TRẠNG THÁI =====================
    private void capNhatTrangThai() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean thongBao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        String s = getString(R.string.status_format,
                camera ? getString(R.string.granted) : getString(R.string.denied),
                thongBao ? getString(R.string.granted) : getString(R.string.denied));
        tvTrangThai.setText(thongTinAnh.isEmpty() ? s : s + "\n" + thongTinAnh);
        Log.d(TAG, s.replace("\n", " | "));
    }
}