package wlfs.largefilesplitter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

@SuppressWarnings("FieldCanBeLocal")
public class MainActivity extends AppCompatActivity {

	private final int STORAGE_PERM_CODE = 1;
	private final int REQ_CODE_GET_SPLIT_FILE = 100;
	private final int REQ_CODE_GET_JOIN_FILE = 150;
	private final int BLOCK_SIZE = 64; // Size of each output part (split) in MB
	private final int BUFFER_SIZE = 1024 * 1024; // 1MB
	private final String SPLIT_FILE_PREFIX = "LFS-part";
	private final String EXTERNAL = Environment.getExternalStorageDirectory().getAbsolutePath();
	/*
	relative path from internal storage
	*/
	private final String SPLIT_FILE_PATH_ = "LFS/partfiles/";
	private final String JOIN_FILE_PATH_ = "LFS/output/";
	private final String WHATSAPP_DIR = "WhatsApp/Media/WhatsApp Documents/";

	private final String SPLIT_FILE_PATH = getSplitFilePath();	// gets full path
	private final String JOIN_FILE_PATH = getJoinFilePath();	// gets full path

	private Button btn_join;
	private Button btn_split;
	private Button btn_browse_split;
	private Button btn_browse_join;
	private TextView tv_output_split;
	private TextView tv_output_join;
	private EditText txt_dir;
	private EditText txt_path;
	private ProgressBar pb;

	private Intent getFileIntent;

	@SuppressLint("StaticFieldLeak")
	class Split extends AsyncTask<String, Integer, Exception> {
		@Override
		protected void onPreExecute() {
			tv_output_split.setText(getString(R.string.split_progress));
			tv_output_join.setText("");
			btn_browse_split.setEnabled(false);
			btn_browse_join.setEnabled(false);
			btn_join.setEnabled(false);
			btn_split.setEnabled(false);
			txt_path.setEnabled(false);
			txt_dir.setEnabled(false);
			setProgressSplit();
		}

		@Override
		protected Exception doInBackground(String... strings) {
			String path = strings[0];
			try {
				File split_path = new File(SPLIT_FILE_PATH);
				if(!split_path.exists()){
					boolean success = split_path.mkdirs();
					if(!success){
						Toast.makeText(MainActivity.this,
								"Unable to create directory to put files in",
								Toast.LENGTH_SHORT).show();
						return new Exception();
					}
				}
				int counter = 0;    // counts no of megabytes of data read
				int num = 1;        // output file prefix
				File f = new File(path);
				String NAME = f.getName();	// along with extension
				FileInputStream fis = new FileInputStream(f);
				FileOutputStream fos = new FileOutputStream(
						new File(SPLIT_FILE_PATH,
								 SPLIT_FILE_PREFIX + num + "-" + NAME + ".LFS" ));

				byte[] buffer = new byte[BUFFER_SIZE];  // 1 MB
				int blocks = (int) Math.ceil(f.length()/(1d*BUFFER_SIZE));
				publishProgress(0,blocks);

				while(fis.read(buffer) != -1){
					publishProgress(counter,blocks);                    // update progressbar
					if(counter % BLOCK_SIZE == 0 && counter != 0){
						num++;                                          // go to next file
						fos.close();                                    // close current output file
						fos = new FileOutputStream(                     // and open a new output file
								new File(SPLIT_FILE_PATH,
										SPLIT_FILE_PREFIX + num + "-" + NAME + ".LFS" ));
					}
					fos.write(buffer);              // write data from input file to output part
					counter ++;                     // increase MB counter
				}
				fos.close();                        // close the last part file
				fis.close();
			}
			catch(IOException ioe){
				ioe.printStackTrace();
				return ioe;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			int num = values[0];
			int blocks = values[1];
			pb.setMax(blocks);
			pb.setProgress(num);

		}

		@Override
		protected void onPostExecute(Exception e) {
			if(e != null){
				Toast.makeText(MainActivity.this,getString(R.string.split_invalid_path),Toast.LENGTH_LONG).show();
				tv_output_split.setText(getString(R.string.split_invalid_path));
			} else{
				tv_output_split.setText(getString(R.string.split_fin));
				Toast.makeText(MainActivity.this,
						getString(R.string.split_savepath,
								SPLIT_FILE_PATH + "/" + SPLIT_FILE_PREFIX),
						Toast.LENGTH_LONG).show();

				new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme)
						.setTitle("Share partfiles")
						.setMessage("Do you want to share partfiles? ")
						.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								shareFiles(getLFSPartFiles(SPLIT_FILE_PATH));
							}
						})
						.setNegativeButton("No", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						})
						.create().show();

			}

			btn_browse_split.setEnabled(true);
			btn_browse_join.setEnabled(true);
			btn_join.setEnabled(true);
			btn_split.setEnabled(true);
			txt_dir.setEnabled(true);
			txt_path.setEnabled(true);
			pb.setProgress(pb.getMax());

			updateTxtOutputJoin();
		}

		@Override
		protected void onCancelled() {

			tv_output_split.setText(getString(R.string.split_cancel));
		}

	}

	@SuppressLint("StaticFieldLeak")
	class Join extends AsyncTask<String, Integer,Exception>{

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			tv_output_join.setText(getString(R.string.join_progress));
			tv_output_split.setText("");
			btn_browse_split.setEnabled(false);
			btn_browse_join.setEnabled(false);
			btn_join.setEnabled(false);
			btn_split.setEnabled(false);
			txt_path.setEnabled(false);
			txt_dir.setEnabled(false);
			setProgressJoin();
		}

		@Override
		protected Exception doInBackground(String... strings) {
			String path = strings[0];
			try {
				File join_path = new File(JOIN_FILE_PATH);
				if(!join_path.exists()){
					boolean success = join_path.mkdirs();
					if (!success){
						Toast.makeText(MainActivity.this,
								"Unable to create directory for output file",
								Toast.LENGTH_SHORT).show();
						return new Exception();
					}
				}
				int counter = 1;    // counts no of megabytes of data read
				File[] partfiles = getLFSPartFiles(path);
				if(partfiles == null || partfiles.length < 1){
					return new Exception();
				}
				String NAME =  removeLFS(partfiles[0].getName());
				String EXTENSION = getExtension(NAME);
				String NAME_WITHOUT_EXTENSION = getName(NAME);
				FileInputStream fis;
				File fout = new File(new File(JOIN_FILE_PATH), NAME);
														// Checking if file already exists,
														// changing name accordingly
				int subFile = 0;
				while(fout.exists()){
					subFile++;
					fout = new File(new File(JOIN_FILE_PATH),
							NAME_WITHOUT_EXTENSION + "_" + subFile + EXTENSION);
				}
				FileOutputStream fos = new FileOutputStream(fout);

				// Calculate total size of all partfiles
				int blocks = 0;
				for (File f : partfiles)
					blocks += (int) Math.ceil(1d*f.length()/BUFFER_SIZE);

				for (File part : partfiles) {
					fis = new FileInputStream(part);
					byte[] buffer = new byte[BUFFER_SIZE];  // 1 MB
					while(fis.read(buffer) != -1){
						publishProgress(counter,blocks); // update progressbar
						fos.write(buffer);              // write data from input part to output file
						counter ++;                     // increase MB counter
					}
					fis.close();					// close the part file
				}
				fos.close();                        // close the output file
			}
			catch(IOException ioe){
				ioe.printStackTrace();
				return ioe;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			int num = values[0];
			int blocks = values[1];
			pb.setMax(blocks);
			pb.setProgress(num);
		}

		@Override
		protected void onPostExecute(Exception e) {
			super.onPostExecute(e);

			if(e != null){
				Toast.makeText(MainActivity.this,getString(R.string.join_invalid_path),Toast.LENGTH_LONG).show();
				tv_output_join.setText(getString(R.string.join_invalid_path));
			} else{
				tv_output_join.setText(getString(R.string.join_fin));
				Toast.makeText(MainActivity.this,
						getString(R.string.join_savepath,
								JOIN_FILE_PATH),
						Toast.LENGTH_LONG).show();

				String delete = getDefaults(getString(R.string.pref_delete_partfiles));
				if(delete == null || delete.equals("")){

					String lfsPartFilesSize =
							getHumanReadableFileSize(
									getFilesSize(
											getLFSPartFiles(
													txt_dir.getText().toString())));

					LinearLayout ll = new LinearLayout(MainActivity.this);
					LinearLayout.LayoutParams params =
							new LinearLayout.LayoutParams(
									ViewGroup.LayoutParams.MATCH_PARENT,
									ViewGroup.LayoutParams.MATCH_PARENT);
					params.gravity = Gravity.CENTER;

					final CheckBox no_confirm = new CheckBox(MainActivity.this);
					no_confirm.setText(R.string.join_delete_partfiles_noconfirm);
					no_confirm.setPadding(20,0,0,0);
					no_confirm.setLayoutParams(params);

					ll.addView(no_confirm);
					ll.setPadding(30,0,0,0);

					new MaterialAlertDialogBuilder(MainActivity.this
							,R.style.AlertDialogTheme)
							.setTitle("Delete PartFiles")
							.setMessage(getString(R.string.join_delete_partfiles,lfsPartFilesSize))
							.setView(ll)
							.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									if(no_confirm.isChecked()){
										setDefaults(
												getString(R.string.pref_delete_partfiles)
												,"true");
									}
									deleteParts(getLFSPartFiles(txt_dir.getText().toString()));
								}
							})
							.setNegativeButton("No", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									if(no_confirm.isChecked()){
										setDefaults(
												getString(R.string.pref_delete_partfiles)
												,"false");
									}
								}
							})
							.show();
				}
				else{
					if(delete.contains("true")){
						deleteParts(getLFSPartFiles(txt_dir.getText().toString()));
					}
				}
			}

			btn_browse_split.setEnabled(true);
			btn_browse_join.setEnabled(true);
			btn_join.setEnabled(true);
			btn_split.setEnabled(true);
			txt_dir.setEnabled(true);
			txt_path.setEnabled(true);
			pb.setProgress(pb.getMax());
			updateTxtOutputSplit();

		}

		@Override
		protected void onCancelled(Exception e) {
			super.onCancelled(e);
			tv_output_split.setText(getString(R.string.join_cancel));

		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		btn_join = findViewById(R.id.btn_join);
		btn_split = findViewById(R.id.btn_split);
		btn_browse_split = findViewById(R.id.btn_browse_split);
		btn_browse_join = findViewById(R.id.btn_browse_join);
		tv_output_split = findViewById(R.id.tv_output_split);
		tv_output_join = findViewById(R.id.tv_output_join);
		txt_path = findViewById(R.id.txt_path);
		txt_dir = findViewById(R.id.txt_dir);
		pb = findViewById(R.id.pb);

		if(!permissionRequired()){
			File join_output_path = new File(JOIN_FILE_PATH);
			File split_output_path = new File(SPLIT_FILE_PATH);
			if(!join_output_path.exists()){
				boolean success = join_output_path.mkdirs();
				String msg = success? "LFS directory made in " + EXTERNAL :
						"Unable to create LFS directory";
				Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
				if(!success)System.exit(1);
			}
			if(!split_output_path.exists()){
				boolean success = split_output_path.mkdirs();
				String msg = success? "LFS directory made in " + EXTERNAL :
						"Unable to create LFS directory";
				Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
				if(!success)System.exit(1);
			}
		}

		txt_dir.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTxtOutputJoin();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}
			@Override
			public void afterTextChanged(Editable s){}
		});

		txt_path.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTxtOutputSplit();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}
			@Override
			public void afterTextChanged(Editable s){}
		});

		btn_split.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(permissionRequired()) return;
				String path = txt_path.getText().toString();
				if(path.equals("")){
					Toast.makeText(MainActivity.this,
							getString(R.string.split_empty_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				deleteParts();
				split(path);
			}
		});

		btn_join.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(permissionRequired()) return;
				final String path = txt_dir.getText().toString();
				if(path.equals("")){
					Toast.makeText(MainActivity.this,
							getString(R.string.join_empty_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				join(path);
			}
		});

		btn_browse_split.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
				startActivityForResult(getFileIntent,REQ_CODE_GET_SPLIT_FILE);
			}
		});

		btn_browse_join.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				getFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(getFileIntent,REQ_CODE_GET_JOIN_FILE);

			}
		});

		handleOpenFileIntent();

		File whatsApp = new File(new File(EXTERNAL), WHATSAPP_DIR);
		if(whatsApp.exists()) {
			txt_dir.setText(whatsApp.getAbsolutePath());
		}

	}

	private void handleOpenFileIntent(){
		if(!txt_path.isEnabled())	return;
		Uri uri = getIntent().getData();
		if(uri == null){
			return;
		}
		String path = FileU.getPath(MainActivity.this, uri);
		if(path == null || path.equals("")){
			Toast.makeText(MainActivity.this,
					"Could not open file",
					Toast.LENGTH_SHORT).show();
		}
		txt_path.setText(path);
		updateTxtOutputSplit();
		setProgressSplit();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (data != null && resultCode == RESULT_OK) {

			if (requestCode == REQ_CODE_GET_SPLIT_FILE) {
				String path = FileU.getPath(MainActivity.this, data.getData());
				if (path.equals("")) {
					Toast.makeText(MainActivity.this,
							getString(R.string.split_unknown_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				txt_path.setText(path);
				updateTxtOutputSplit();
				setProgressSplit();
			}

			if (requestCode == REQ_CODE_GET_JOIN_FILE) {
				String path = FileU.getTree(data.getData());
				if (path.equals("")) {
					Toast.makeText(MainActivity.this,
							getString(R.string.join_unknown_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				txt_dir.setText(path);
				updateTxtOutputJoin();
				setProgressJoin();

			}
		}
	}

	private void updateTxtOutputJoin(){

		String path = txt_dir.getText().toString();
		if(path.equals("")) {
			tv_output_join.setText("");
			return;
		}
		File[] partFiles = getLFSPartFiles(path);
		if(partFiles == null){
			tv_output_join.setText(getString(R.string.join_invalid_path));
			return;
		}
		String msg = getString(R.string.join_found,getFolderName(path),partFiles.length);
		tv_output_join.setText(msg);
	}

	private void updateTxtOutputSplit(){
		String path = txt_path.getText().toString();
		if(path.equals("")) {
			tv_output_split.setText("");
			return;
		}
		File file = new File(path);
		if(!file.exists()){
			tv_output_split.setText(getString(R.string.split_invalid_path));
			return;
		}
		long bytes = file.length();

		String size = getHumanReadableFileSize(bytes);
		int parts = (int) Math.ceil(1d*bytes/(BLOCK_SIZE*BUFFER_SIZE));
		String block_size = getHumanReadableFileSize(BLOCK_SIZE*BUFFER_SIZE);
		String plural = (parts > 1)?"parts":"part";
		tv_output_split.setText(String.format(Locale.getDefault(),
				"%.40s%n%s%n will be split into %d %s (%s each)",
				file.getName(), size,parts,plural,block_size));
	}

	private void deleteParts(){
		File[] LFSPartFiles = getLFSPartFiles(SPLIT_FILE_PATH);
		if(LFSPartFiles == null || LFSPartFiles.length < 1)	return;
		for(File file : LFSPartFiles){
			boolean success = file.delete();
			if(!success){
				Log.d("ERROR","Unable to delete file "+file.getAbsolutePath());
			}
		}
	}

	private void deleteParts(File[] LFSPartFiles){
		if(LFSPartFiles == null || LFSPartFiles.length < 1)	return;
		for(File file : LFSPartFiles){
			boolean success = file.delete();
			if(!success){
				Log.d("ERROR","Unable to delete file "+file.getAbsolutePath());
			}
		}
	}

	private String getHumanReadableFileSize(long bytes){
		double sizeh = bytes;
		double GB = 1024 * 1024 * 1024;
		double MB = 1024 * 1024;
		double KB = 1024;
		String unit = "bytes";
		if(bytes > GB){
			sizeh = bytes/GB ;
			unit = "GB";
		}
		else if(bytes > MB){
			sizeh = bytes/MB ;
			unit = "MB";
		}
		else if(bytes > KB){
			sizeh = bytes/KB ;
			unit = "KB";
		}
		return String.format(Locale.getDefault(),"%3.2f %s",sizeh,unit);
	}

	private boolean permissionRequired(){
		if(ContextCompat.checkSelfPermission(MainActivity.this, WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED){
			return false;
		}
		else requestExternalStoragePermission();
		return (ContextCompat.checkSelfPermission(MainActivity.this, WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED);
	}

	private void split(String... args){
		new Split().execute(args);
	}

	private void join (String... args){
		File [] files = getLFSPartFiles(args[0]);
		if(files == null){
			Toast.makeText(MainActivity.this,
					"Invalid directory",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (files.length < 1){
			Toast.makeText(MainActivity.this,
					"No LFS files in directory",
					Toast.LENGTH_SHORT).show();
			return;
		}
		new Join().execute(args);
	}

	private void requestExternalStoragePermission(){
		new AlertDialog.Builder(MainActivity.this)
				.setTitle(getString(R.string.permission_title))
				.setMessage(getString(R.string.permission_body))
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						ActivityCompat.requestPermissions(MainActivity.this,
								new String[] {WRITE_EXTERNAL_STORAGE},STORAGE_PERM_CODE);
					}
				})
				.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						System.exit(-1);
					}
				})
				.create().show();
	}

	private String getExtension(String fileName){
		int ind = fileName.lastIndexOf(".");
		if(ind == -1) return "";
		return fileName.substring(ind);
	}
	private void shareFiles(File[] partFiles) {
		if(partFiles == null || partFiles.length < 1)	return;
		ArrayList<Uri> uris = new ArrayList<>();
		for(File file : partFiles){
			uris.add(
					FileProvider.getUriForFile(
					MainActivity.this,
					MainActivity.this.getApplicationContext().getPackageName()+".provider",
					file
			));
		}
		Intent intentShareFile = new Intent(Intent.ACTION_SEND_MULTIPLE);
		intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intentShareFile.setType("*/*");
		intentShareFile.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
		startActivity(Intent.createChooser(intentShareFile, "Share File"));
	}

	/**
	 *	Removes LFS tags from partfiles (leading `SPLIT_FILE_PREFIX` and trailing `.LFS`)
	 * @param filename name of partfile
	 * @return String: filename without LFS tags
	 */
	private String removeLFS(String filename){
		String rv = filename.substring(0,filename.length()-4);	// removing trailing .LFS
		int ind = filename.indexOf(SPLIT_FILE_PREFIX) + SPLIT_FILE_PREFIX.length();
		ind = filename.indexOf("-",ind) + 1;
		return rv.substring(ind);
	}

	private File[] getLFSPartFiles(String path){
		return new File(path).listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(SPLIT_FILE_PREFIX) && name.endsWith(".LFS");
			}
		});
	}
	private String getFolderName(String path){
		File f = new File(path);
		if(f.equals(new File(EXTERNAL))){
			return "Internal Storage";
		}
		else return f.getName();
	}

	private void setProgressSplit(){
		pb.setProgress(0);
		pb.setVisibility(View.VISIBLE);
		pb.getProgressDrawable().setColorFilter(
				ContextCompat.getColor(MainActivity.this, R.color.color) ,
				android.graphics.PorterDuff.Mode.SRC_IN);

	}
	private void setProgressJoin(){
		pb.setProgress(0);
		pb.setVisibility(View.VISIBLE);
		pb.getProgressDrawable().setColorFilter(
				ContextCompat.getColor(MainActivity.this, R.color.colorAccent) ,
				android.graphics.PorterDuff.Mode.SRC_IN);
	}

	private void setDefaults(String field, String value){
		getPreferences(Context.MODE_PRIVATE).edit().putString(field, value).apply();
	}

	private String getDefaults(String field){
		return getPreferences(Context.MODE_PRIVATE).getString(field,null);
	}

	private String getName(String filename) {
		int ind = filename.lastIndexOf(".");
		if(ind == -1) return filename;
		return filename.substring(0,ind);
	}

	private long getFilesSize(File[] files){
		if(files == null || files.length < 1) return 0L;
		long filesSize = 0L;
		for(File file : files){
			filesSize += file.length();
		}
		return filesSize;
	}

	private String getSplitFilePath(){
		return new File(new File(EXTERNAL),SPLIT_FILE_PATH_).getAbsolutePath();
	}
	private String getJoinFilePath(){
		return new File(new File(EXTERNAL),JOIN_FILE_PATH_).getAbsolutePath();
	}

}
