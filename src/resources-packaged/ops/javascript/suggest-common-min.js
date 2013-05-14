function addEvent(_1,_2,_3){if(_1.attachEvent){_1.attachEvent("on"+_2,_3);}else{if(_1.addEventListener){_1.addEventListener(_2,_3,true);}else{_1["on"+_2]=_3;}}}function removeEvent(_4,_5,_6){if(_4.detachEvent){_4.detachEvent("on"+_5,_6);}else{if(_4.removeEventListener){_4.removeEventListener(_5,_6,true);}else{_4["on"+_5]=null;}}}function stopEvent(_7){_7||window.event;if(_7.stopPropagation){_7.stopPropagation();_7.preventDefault();}else{if(typeof _7.cancelBubble!="undefined"){_7.cancelBubble=true;_7.returnValue=false;}}return false;}function getElement(_8){if(window.event){return window.event.srcElement;}else{return _8.currentTarget;}}function getTargetElement(_9){if(window.event){return window.event.srcElement;}else{return _9.target;}}function stopSelect(_a){if(typeof _a.onselectstart!="undefined"){addEvent(_a,"selectstart",function(){return false;});}}function getCaretEnd(_b){if(typeof _b.selectionEnd!="undefined"){return _b.selectionEnd;}else{if(document.selection&&document.selection.createRange){var M=document.selection.createRange();try{var Lp=M.duplicate();Lp.moveToElementText(_b);}catch(e){var Lp=_b.createTextRange();}Lp.setEndPoint("EndToEnd",M);var rb=Lp.text.length;if(rb>_b.value.length){return -1;}return rb;}}}function getCaretStart(obj){if(typeof obj.selectionStart!="undefined"){return obj.selectionStart;}else{if(document.selection&&document.selection.createRange){var M=document.selection.createRange();try{var Lp=M.duplicate();Lp.moveToElementText(obj);}catch(e){var Lp=obj.createTextRange();}Lp.setEndPoint("EndToStart",M);var rb=Lp.text.length;if(rb>obj.value.length){return -1;}return rb;}}}function setCaret(obj,l){obj.focus();if(obj.setSelectionRange){obj.setSelectionRange(l,l);}else{if(obj.createTextRange){m=obj.createTextRange();m.moveStart("character",l);m.collapse();m.select();}}}function setSelection(obj,s,e){obj.focus();if(obj.setSelectionRange){obj.setSelectionRange(s,e);}else{if(obj.createTextRange){m=obj.createTextRange();m.moveStart("character",s);m.moveEnd("character",e);m.select();}}}String.prototype.addslashes=function(){return this.replace(/(["\\\.\|\[\]\^\*\+\?\$\(\)])/g,"\\$1");};String.prototype.trim=function(){return this.replace(/^\s*(\S*(\s+\S+)*)\s*$/,"$1");};function curTop(obj){toreturn=0;while(obj){toreturn+=obj.offsetTop;obj=obj.offsetParent;}return toreturn;}function curLeft(obj){toreturn=0;while(obj){toreturn+=obj.offsetLeft;obj=obj.offsetParent;}return toreturn;}function isNumber(a){return typeof a=="number"&&isFinite(a);}function replaceHTML(obj,_1e){while(el=obj.childNodes[0]){obj.removeChild(el);}obj.appendChild(document.createTextNode(_1e));}