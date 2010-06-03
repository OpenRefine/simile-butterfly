package edu.mit.simile.butterfly;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is a special PrintWriter that is capable of incrementally looking 
 * for strings of the form "[#name#]" and replace the module "name" with 
 * the URL where the named module is actually mounted. This allows modules
 * to be agnostic to where their dependencies are mount, making it easier
 * to reuse modules across different web applications.
 * 
 * NOTE: great care has been taken in making sure that this writer is
 * incremental, meaning that works by minimizing the amount of buffering
 * that it needs to operate. This is because we don't want such rewriting
 * to be an impact for the perceived performance of responses. 
 */
public class LinkRewriter extends PrintWriter {

    private static final Logger _logger = LoggerFactory.getLogger("butterfly.link_rewriter");
    
    private ButterflyModule _module;
    private PrintWriter _writer;
    private char[] _baseURL;

    public LinkRewriter(PrintWriter writer, ButterflyModule module) {
        this(writer, module, null);
    }
    
    public LinkRewriter(PrintWriter writer, ButterflyModule module, String baseURL) {
        super(writer);
        _writer = writer;
        _module = module;
        _baseURL = (baseURL == null) ? null : baseURL.toCharArray();
    }
    
    public enum State {
        START, OPENING, OPENED, CLOSING, CLOSED, SLASHED, UNSLASHED
    }   

    private State _state = State.START;
    private int _start = -1;
    private int _end = -1;
    private StringBuffer _buffer = new StringBuffer(128);
    
    @Override
    public void write(char[] buf, int off, int len) {
        _logger.trace("write char[] {} {}", off, len);
        if (off >= buf.length) return;
        len = Math.min(off + len, buf.length) - off;
        for (int i = off; i < off + len; i++) {
            char c = buf[i];
            _logger.trace("'{}'", c);
            
            if (_state == State.START) {
                if (c == '[') {
                    _logger.trace("start -> opening");
                    _state = State.OPENING;
                    _start = i;
                }
            } else if (_state == State.OPENING) {
                if (c == '#') {
                    _logger.trace("opening -> opened");
                    _state = State.OPENED;
                } else {
                    _logger.trace("opening -> start");
                    _state = State.START;
                }
            } else if (_state == State.OPENED) {
                if (c == '#') {
                    _logger.trace("opened -> closing");
                    _state = State.CLOSING;
                } else if (!Character.isJavaIdentifierPart(c)) {
                    _logger.trace("opened -> start");
                    _state = State.START;
                }
            } else if (_state == State.CLOSING) {
                if (c == ']') {
                    _logger.trace("closing -> closed");
                    _state = State.CLOSED;
                } else {
                    _logger.trace("closing -> start");
                    _state = State.START;
                }
            } else if (_state == State.CLOSED) {
                _end = i - 1;
                if (c == '/') {
                    _logger.trace("closed -> slashed");
                    _state = State.SLASHED;
                } else {
                    _logger.trace("closed -> unslashed");
                    _state = State.UNSLASHED;
                }
                break;
            }
        }
        
        if (_state == State.START) {
            _logger.trace("pass along");
            int l = _buffer.length();
            if (l > 0) {
                char[] b = new char[l];
                _buffer.getChars(0, l, b, 0);
                _writer.write(b, 0, l);
                _buffer.setLength(0);
            }
            _writer.write(buf, off, len); // just pass the data along, since there is nothing to rewrite
        } else if (_state == State.SLASHED || _state == State.UNSLASHED) {
            boolean slashed = (_state == State.SLASHED);
            _state = State.START;
            _logger.trace("closed");
            if (_buffer.length() > 0) {
                int l = _buffer.length();
                _logger.trace("with leftovers: {}", l);
                _buffer.append(buf,off,len);
                len = _buffer.length();
                buf = new char[len];
                off = 0;
                _end += l;
                _buffer.getChars(0, len, buf, 0);
                _buffer.setLength(0);
            }
            _logger.trace("{} {}", _start, _end);
            String name = new String(buf, _start + 2, _end - _start - 3);
            _logger.trace("name: {}", name);
            ButterflyModule module = _module.getModule(name);
        	MountPoint mountPoint = (module == null) ? null : module.getMountPoint();
            if (mountPoint != null) {
                _logger.trace("module found");
                _writer.write(buf, off, _start - off);
                if (this._baseURL != null) _writer.write(_baseURL, 0, _baseURL.length);
                char[] mountPointChars = mountPoint.getMountPoint().toCharArray();
                _writer.write(mountPointChars, 0, slashed ? mountPointChars.length - 1 : mountPointChars.length);
                write(buf, _end+1, off + len - _end - 1);
            } else {
                _logger.trace("module NOT found");
                _writer.write(buf, off, _end - off); // write the part processed so far
                write(buf, _end, len - (_end - off)); // and continue processing recursively
            }
        } else { // in case we are in an intermediate state, we can only store and wait for more data
            if (_buffer.length() == 0) {
                _start -= off; // adjust the starting point (only the first time)  
            }
            _buffer.append(buf,off,len); 
            _logger.trace("saved leftovers: {} {}", _buffer.length(), _start);
        }
    }

    // ---- all the write methods converge to the one above -----
    
    @Override
    public void write(char[] buf) {
        _logger.trace("write char[]");
        write(buf, 0, buf.length);
    }

    @Override
    public void write(String s) {
        _logger.trace("write string");
        write(s.toCharArray(), 0, s.length());
    }

    @Override
    public void write(String s, int off, int len) {
        _logger.trace("write string off len");
        write(s.toCharArray(), off, len);
    }
    
    @Override
    public void write(int c) {
        _logger.trace("write int");
        write(new char[] { (char) c }, 0, 1);
    }
}
