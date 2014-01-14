package org.brailleblaster.perspectives.braille.views.tree;

import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Text;

import org.brailleblaster.BBIni;
import org.brailleblaster.perspectives.braille.Manager;
import org.brailleblaster.perspectives.braille.document.BBSemanticsTable;
import org.brailleblaster.perspectives.braille.mapping.TextMapElement;
import org.brailleblaster.perspectives.braille.messages.Message;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class BookTree extends TreeView {
	private class TreeItemData {
		public TextMapElement element;
		public int startRange, endRange;
		
		public TreeItemData(TextMapElement element, int startRange){
			this.element = element;
			this.startRange = startRange;
			this.endRange = -1;
		}		
	}
	
	private TreeItem root, previousItem, lastParent;
	private BBSemanticsTable table;
	private SelectionListener selectionListener;
	private FocusListener focusListener;
	
	public BookTree(final Manager dm, Group documentWindow){
		super(dm, documentWindow);
		table = dm.getStyleTable();
		this.tree.pack();
	}
	
	@Override
	public void resetView(Group group) {
		setListenerLock(true);
		this.root.setExpanded(false);
		this.root.dispose();
		this.root = null;
		setListenerLock(false);
	}

	@Override
	public void initializeListeners(final Manager dm) {
		this.tree.addSelectionListener(selectionListener = new SelectionListener(){
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				
			}

			@Override
			public void widgetSelected(SelectionEvent e) {
				if(!getLock() && tree.getSelection()[0].equals(e.item)){
					if(!e.item.equals(root)){
						TreeItemData data = getItemData((TreeItem)e.item);
						Message m = Message.createSetCurrentMessage("tree", data.element.start, false);
						setCursorOffset(0);
						dm.dispatch(m);
					
						System.out.println(getStartRange(tree.getSelection()[0]) + " " + getEndRange(tree.getSelection()[0]));
					}
				}
			}
		});
		
		tree.addFocusListener(focusListener = new FocusListener(){
			@Override
			public void focusGained(FocusEvent e) {

			}

			@Override
			public void focusLost(FocusEvent e) {
				if(tree.getItemCount() > 0){
					Message cursorMessage = Message.createUpdateCursorsMessage("tree");
					dm.dispatch(cursorMessage);
				}
			}
			
		});
	}

	@Override
	public void removeListeners() {
		tree.removeSelectionListener(selectionListener);	
		tree.removeFocusListener(focusListener);
	}

	@Override
	public void setRoot(Element e) {
		root = new TreeItem(tree, SWT.LEFT | SWT.BORDER);
		
		if(manager.getDocumentName() != null)
			root.setText(manager.getDocumentName().substring(manager.getDocumentName().lastIndexOf(BBIni.getFileSep()) + 1, manager.getDocumentName().lastIndexOf(".")));
		else
			root.setText("Untitled");
		
		setTree(e, root);
		previousItem = null;
	}
	
	public void setTree(Element e, TreeItem item){
		Elements els = e.getChildElements();
		for(int i = 0; i < els.size(); i++){
			if(table.getKeyFromAttribute(els.get(i)).contains("heading") || table.getKeyFromAttribute(els.get(i)).contains("header")){
				TreeItem childItem = new TreeItem(findCorrectLevel(item, els.get(i)), SWT.LEFT | SWT.BORDER);
				setNewItemData(childItem, els.get(i));
				setTree(els.get(i), childItem);
				
				if(!childItem.isDisposed())
					item = childItem;
			}
			else if(!els.get(i).getLocalName().equals("brl"))
				setTree(els.get(i), item);
		}
	}
	
	private TreeItem findCorrectLevel(TreeItem item, Element e){
		if(!item.equals(root)){
			String level = table.getKeyFromAttribute(e);
			Integer levelValue = Integer.valueOf(level.substring(level.length() - 1));
	
			Integer itemValue = Integer.valueOf(item.getText().substring(item.getText().length() - 2, item.getText().length() - 1));
		
			//item = item.getParentItem();
			while(itemValue >= levelValue){
				item = item.getParentItem();
				if(!item.equals(root))
					itemValue = Integer.valueOf(item.getText().substring(item.getText().length() - 2, item.getText().length() - 1));
				else
					break;
			}
		}
		return item;
	}
	
	//Finds text for tree item and sets corresponding data
	private void setNewItemData(TreeItem item, Element e){
		int start = findIndex(item, e);
		
		if(start != -1){
			item.setText(formatItemText(e));
			item.setData(new TreeItemData(manager.getTextMapElement(start), start));
			if(previousItem != null){
				((TreeItemData)previousItem.getData()).endRange = start - 1;
			}
		
			previousItem = item;
		}
		else if(item.getItemCount() == 0)
			item.dispose();
	}
	
	private int findIndex(TreeItem item, Element e){
		for(int i = 0; i < e.getChildCount(); i++){
			if(e.getChild(i) instanceof Text){
				int searchIndex;
				
				if(previousItem == null)
					searchIndex = 0;
				else
					searchIndex = getItemData(previousItem).startRange + 1;
				
				return manager.findNodeIndex(e.getChild(i), searchIndex);
			}
			else if(e.getChild(i) instanceof Element && !((Element)e.getChild(i)).getLocalName().equals("brl") &&  !((Element)e.getChild(i)).getLocalName().equals("img") && table.getSemanticTypeFromAttribute((Element)e.getChild(i)).equals("action"))
				return findIndex(item, (Element)e.getChild(i));
		}
		
		return -1;
	}
	
	private String formatItemText(Element e){
		String text = getText(e);
		text += getHeading(e);
		
		return text;
	}

	private String getText(Element e){
		String text = "";
		for(int i = 0; i < e.getChildCount(); i++){
			if(e.getChild(i) instanceof Text)
				text += e.getChild(i).getValue();
			else if(e.getChild(i) instanceof Element && !((Element)e.getChild(i)).getLocalName().equals("brl") && table.getSemanticTypeFromAttribute((Element)e.getChild(i)).equals("action"))
				text += getText((Element)e.getChild(i));
		}
		
		return text.trim();
	}
	
	private String getHeading(Element e){
		
		if(table.getSemanticTypeFromAttribute(e).equals("action"))
			return getHeading((Element)e.getParent());
		
		return " (" + table.getKeyFromAttribute(e) + ")";
	}
	
	@Override
	public TreeItem getRoot() {
		return root;
	}

	@Override
	public void newTreeItem(TextMapElement t, int index) {
		if(isHeading(t)){
			TreeItem temp;
			if(lastParent != null)
				temp = new TreeItem(lastParent, SWT.None, index);
			else
				temp = new TreeItem(root, SWT.None, index);
			
			temp.setText(t.getText());
			temp.setData(new TreeItemData(t, manager.indexOf(t)));
			resetIndexes();
		}
	}

	@Override
	public void removeCurrent() {
		setListenerLock(true);
		TreeItemData data  = getItemData(tree.getSelection()[0]);
		if(data != null && isHeading(data.element)){
			lastParent = tree.getSelection()[0].getParentItem();
			tree.getSelection()[0].dispose();
			
			resetIndexes();
		}
		setListenerLock(false);
		System.out.println("removeCurrent called");
	}

	@Override
	public void removeItem(TextMapElement t, Message m) {
		setListenerLock(true);
		int index = manager.indexOf(t);
		TreeItem item = findRange(manager.indexOf(t));
		
		if(isHeading(t)){
			if(item.getItemCount() > 0)
				copyItemChildren(item);
			
			item.dispose();
		}
		
		updateIndexes(root, index, false);
		if(root.getItemCount() > 0)
			getItemData(getLastItem()).endRange = -1;
		
		setListenerLock(false);
	}
	
	
	private boolean isHeading(TextMapElement t){
		Element parent = t.parentElement();
		if(table.getSemanticTypeFromAttribute(parent).equals("action")){
			parent = (Element)t.parentElement().getParent();
			while(!table.getSemanticTypeFromAttribute(parent).equals("style")){
				parent = (Element)parent.getParent();
			}
		}
		
		if(table.getKeyFromAttribute(parent).contains("heading"))
			return true;
		else
			return false;
	}
	
	private void copyItemChildren(TreeItem item){
		TreeItem parent = item.getParentItem();
		int itemIndex = parent.indexOf(item);
		if(itemIndex > 0)
			copyChildrenHelper(item, parent.getItem(itemIndex - 1));
		else
			copyChildrenHelper(item, parent);
	}
	
	private void copyChildrenHelper(TreeItem original, TreeItem newParent){
		int count = original.getItemCount();
		for(int i = 0; i < count; i++){
			TreeItem temp = new TreeItem(newParent, SWT.NONE);
			temp.setText(original.getItem(i).getText());
			temp.setData(original.getItem(i).getData());
			if(original.getItem(i).getItemCount() > 0)
				copyChildrenHelper(original.getItem(i), temp);
		}
	}
	
	private void updateIndexes(TreeItem item, int index, boolean found){
		for(int i = 0; i < item.getItemCount(); i++){
			TreeItemData data = getItemData(item.getItem(i));
			
			if(!found){
				if(data.endRange >= index ||(data.endRange == -1 && !item.getItem(i).equals(getLastItem())))
					found = true;
			}
			
			if(found){
				if(data.startRange != 0 && data.startRange >= index)	
					data.startRange--;
				
				if(data.endRange != -1 && data.endRange >= index)
					data.endRange--;
				else if(data.endRange == -1 && !item.getItem(i).equals(getLastItem()))
					data.endRange = data.startRange;
			}
			
			if(item.getItem(i).getItemCount() > 0)
				updateIndexes(item.getItem(i), index, found);
		}
	}
	
	private TreeItem getLastItem(){
		TreeItem item = root;
		while(item.getItemCount() > 0){
			item = item.getItem(item.getItemCount() - 1);
		}
		
		if(!item.equals(root))
			return item;
		else
			return null;
	}

	@Override
	public void removeMathML(TextMapElement t, Message m) {	
	
	}

	@Override
	public int getBlockElementIndex() {
		return getSelectionIndex();
	}

	@Override
	public void setSelection(TextMapElement t, Message message) {		
		setListenerLock(true);
		TreeItem item = findRange(manager.indexOf(t));
		if(item != null){
			tree.setSelection(item);
		}
		else
			tree.setSelection(root);
		
		setListenerLock(false);
	}

	@Override
	public TextMapElement getSelection(TextMapElement t) {
		return t;
	}

	@Override
	public int getSelectionIndex() {
		if(tree.getSelection().length != 0 && !tree.getSelection()[0].equals(root)){
			TreeItem parent = tree.getSelection()[0].getParentItem();
			return parent.indexOf(tree.getSelection()[0]);
		}
		else
			return 0;
	}

	@Override
	public void clearTree() {
		tree.removeAll();	
	}

	@Override
	public StyledText getView() {
		return view;
	}

	@Override
	public Tree getTree() {
		return tree;
	}
	
	private TreeItemData getItemData(TreeItem item){
		return (TreeItemData)item.getData();
	}
	
	private TreeItem findRange(int index){
		return searchTree(root, index);
	}
	
	private TreeItem searchTree(TreeItem item, int index){
		TreeItem [] items = item.getItems();
		
		for(int i = 0; i < items.length; i++){
			if(checkItem(items[i], index))
				return items[i];
			else if(i < items.length - 1){
				if(index > getEndRange(items[i]) && index < getStartRange(items[i + 1]))
					return searchTree(items[i], index);
			}
			else if(i == items.length - 1 && index > getStartRange(items[i]) && items[i].getItemCount() > 0) {
				return searchTree(items[i], index);
			}
		}
		
		return null;
	}
	
	private void resetIndexes(){
		resetIndexesHelper(root);
		previousItem = null;
	}
	private void resetIndexesHelper(TreeItem item){
		int count = item.getItemCount();
		for(int i = 0; i < count; i++){
			TreeItemData data = getItemData(item.getItem(i));
			data.startRange = manager.indexOf(data.element);
			
			if(previousItem != null){
				if(previousItem != null){
					((TreeItemData)previousItem.getData()).endRange = data.startRange - 1;
				}
			}
			previousItem = item.getItem(i);
			
			if(item.getItem(i).getItemCount() > 0)
				resetIndexesHelper(item.getItem(i));
		}
	}
	
	private int getStartRange(TreeItem item){
		return ((TreeItemData)item.getData()).startRange;
	}
	
	private int getEndRange(TreeItem item){
		return ((TreeItemData)item.getData()).endRange;
	}
	
	private boolean checkItem(TreeItem item, int index){
		TreeItemData data = getItemData(item);
		if((index >= data.startRange && index <= data.endRange) || (index >= data.startRange && data.endRange == -1))
			return true;
		else
			return false;
	}

	public void split(Message m) {
		lastParent = tree.getSelection()[0].getParentItem();
		TreeItem item = tree.getSelection()[0];
		int firstElementIndex = (Integer)m.getValue("firstElementIndex");
		int secondElementIndex = (Integer)m.getValue("secondElementIndex");
		int treeIndex = (Integer)m.getValue("treeIndex");
		
		if(isHeading(manager.getTextMapElement(firstElementIndex)))
			newTreeItem(manager.getTextMapElement(firstElementIndex), treeIndex);
		
		if(isHeading(manager.getTextMapElement(secondElementIndex))){
			if(!item.equals(root)){
				item.setText(manager.getTextMapElement(secondElementIndex).getText());
				item.setData(new TreeItemData(manager.getTextMapElement(secondElementIndex), manager.indexOf(manager.getTextMapElement(secondElementIndex))));
			}
			else {
				newTreeItem(manager.getTextMapElement(secondElementIndex), treeIndex + 1);
			}
		}
		
		resetIndexes();		
	}

	public void adjustItemStyle(TextMapElement t) {
		if(isHeading(t)){
			tree.removeAll();
			setRoot(manager.getDocument().getRootElement());
			TreeItem item = findRange(manager.indexOf(t));
			
			tree.setSelection(item);
			
			item = item.getParentItem();
			while(item != null){
				item.setExpanded(true);
				item = item.getParentItem();
			}
		}
	}
}
