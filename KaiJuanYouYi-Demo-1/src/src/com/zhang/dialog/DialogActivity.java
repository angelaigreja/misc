package src.com.zhang.dialog;

import src.com.zhang.menu.MenuItem;
import src.com.zhang.menu.MyMenu;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Toast;

/**
 * ˽��
 * 
 * @author Administrator
 * 
 */
public class DialogActivity extends Activity implements MenuItem
{

	private String myMenuStr[] = { "�洢��", "�ҵ�����", "ͼ�鵼��", "ϵͳ����", "ϵͳ�ָ�",
			"���ȫ��", "��������", "��������", "���ڿ���", "�˳�ϵͳ" };

	private int myMenuBit[] = { R.drawable.icon_sdcard, R.drawable.icon_sdcard,
			R.drawable.icon_sdcard, R.drawable.icon_sdcard,
			R.drawable.icon_sdcard, R.drawable.icon_sdcard,
			R.drawable.icon_sdcard, R.drawable.icon_sdcard,
			R.drawable.icon_sdcard, R.drawable.icon_sdcard };

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	// ------------------------------------------------MENU�¼�
	/**
	 * ����MENU
	 */

	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add("menu");// ���봴��һ��
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * ����MENU
	 */

	public boolean onMenuOpened(int featureId, Menu menu)
	{

		new MyMenu(this, myMenuStr, myMenuBit, this).show();
		return false; // ����Ϊtrue ����ʾϵͳmenu
	}

	@Override
	public void ItemClickListener(int position)
	{
		Toast.makeText(this, "��ѡ�е�" + (position + 1) + "��", Toast.LENGTH_SHORT)
				.show();
	}
}