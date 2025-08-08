package hu.doris.albumartdownloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;
    private ProgressBar prog;
    private Uri treeUri;
    private List<DocumentFile> mp3Files = new ArrayList<>();
    private OkHttpClient httpClient = new OkHttpClient();

    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        prog = findViewById(R.id.prog);
        Button btnChoose = findViewById(R.id.btnChoose);
        Button btnStart = findViewById(R.id.btnStart);

        folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    treeUri = result.getData().getData();
                    getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    tvStatus.setText("Mappa kiválasztva: " + treeUri.getPath());
                    scanMp3Files();
                }
            }
        );

        btnChoose.setOnClickListener(v -> openFolderPicker());
        btnStart.setOnClickListener(v -> {
            if (mp3Files.isEmpty()) {
                tvStatus.setText("Nincs mp3 a kiválasztott mappában.");
                return;
            }
            prog.setVisibility(View.VISIBLE);
            new Thread(() -> {
                for (DocumentFile df : mp3Files) {
                    try {
                        runOnUiThread(() -> tvStatus.setText("Feldolgozás: " + df.getName()));
                        MediaFileProcessor.processAndEmbed(this, df, httpClient);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                runOnUiThread(() -> {
                    prog.setVisibility(View.GONE);
                    tvStatus.setText("Kész — feldolgozott: " + mp3Files.size());
                });
            }).start();
        });
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }

    private void scanMp3Files() {
        mp3Files.clear();
        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
        if (pickedDir == null) return;
        for (DocumentFile f : pickedDir.listFiles()) {
            if (f.isFile() && f.getName() != null && f.getName().toLowerCase().endsWith(".mp3")) mp3Files.add(f);
            else if (f.isDirectory()) {
                for (DocumentFile sub : f.listFiles())
                    if (sub.isFile() && sub.getName() != null && sub.getName().toLowerCase().endsWith(".mp3"))
                        mp3Files.add(sub);
            }
        }
        tvStatus.setText("Talált mp3: " + mp3Files.size());
    }
}
