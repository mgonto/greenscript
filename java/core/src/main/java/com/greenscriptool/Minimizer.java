package com.greenscriptool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.greenscriptool.utils.BufferLocator;
import com.greenscriptool.utils.FileCache;
import com.greenscriptool.utils.FileResource;
import com.greenscriptool.utils.GreenScriptCompressor;
import com.greenscriptool.utils.IBufferLocator;
import com.greenscriptool.utils.ICompressor;

public class Minimizer implements IMinimizer {
    
    private static Log logger_ = LogFactory.getLog(Minimizer.class);
    
    private boolean minimize_;
    private boolean compress_;
    private boolean useCache_;
    private boolean inMemory_;
    
    private FileCache cache_;
    private String resourceDir_;
    private String rootDir_;
    
    private String resourceUrlRoot_ = "";
    private String resourceUrlPath_;
    private String cacheUrlPath_;
    
    private ICompressor compressor_;
    private ResourceType type_;
    
    public Minimizer(ResourceType type) {
        this(new GreenScriptCompressor(type), type);
    }
    
    @Inject
    public Minimizer(ICompressor compressor, ResourceType type) {
        if (null == compressor) throw new NullPointerException();
        compressor_ = compressor;
        type_ = type;
    }

    @Override
    public void enableDisableMinimize(boolean enable) {
        minimize_ = enable;
        if (logger_.isDebugEnabled()) logger_.debug("minimize " + (enable ? "enabled" : "disabled"));
        clearCache();
    }

    @Override
    public void enableDisableCompress(boolean enable) {
        compress_ = enable;
        if (logger_.isDebugEnabled()) logger_.debug("compress " + (enable ? "enabled" : "disabled"));
        clearCache();
    }

    @Override
    public void enableDisableCache(boolean enable) {
        useCache_ = enable;
        if (logger_.isDebugEnabled()) logger_.debug("cache " + (enable ? "enabled" : "disabled"));
        clearCache();
    }
    
    @Override
    public void enableDisableInMemoryCache(boolean enable) {
        inMemory_ = enable;
        if (logger_.isDebugEnabled()) logger_.debug("in memory cache " + (enable ? "enabled" : "disabled"));
        clearCache();
    }
    
    @Deprecated
    public void enableDisableVerifyResource(boolean verify) {
        //verifyResource_ = verify;
    }

    @Override
    public boolean isMinimizeEnabled() {
        return minimize_;
    }

    @Override
    public boolean isCompressEnabled() {
        return compress_;
    }

    @Override
    public boolean isCacheEnabled() {
        return useCache_;
    }
    
    @Override
    public void setResourceDir(String dir) {
        //if (!dir.isDirectory()) throw new IllegalArgumentException("not a directory");
        checkInitialize_(false);
        resourceDir_ = dir;
    }
    
    @Override
    public void setRootDir(String dir) {
        //if (!dir.isDirectory()) throw new IllegalArgumentException("not a directory");
        checkInitialize_(false);
        rootDir_ = dir;
        if (logger_.isDebugEnabled()) logger_.debug(String.format("root dir set to %1$s", dir));
    }
    
    @Override
    public void setCacheDir(File dir) {
        if (!dir.isDirectory() && !dir.mkdir()) throw new IllegalArgumentException("not a dir");
        checkInitialize_(false);
        cache_ = new FileCache(dir);
    }
    
    @Override
    public void setResourceUrlRoot(String urlRoot) {
        if (!urlRoot.startsWith("/")) throw new IllegalArgumentException("url root must start with /");
        checkInitialize_(false);
        if (!urlRoot.endsWith("/")) urlRoot = urlRoot + "/";
        resourceUrlRoot_ = urlRoot;
        if (logger_.isDebugEnabled()) logger_.debug(String.format("url root set to %1$s", urlRoot));
    }
    
    @Override
    public void setResourceUrlPath(String urlPath) {
        if (!urlPath.startsWith("/")) throw new IllegalArgumentException("url path must start with /");
        checkInitialize_(false);
        if (!urlPath.endsWith("/")) urlPath = urlPath + "/";
        resourceUrlPath_ = urlPath;
        if (logger_.isDebugEnabled()) logger_.debug(String.format("url path set to %1$s", urlPath));
    }
    
    @Override
    public void setCacheUrlPath(String urlPath) {
        if (!urlPath.startsWith("/")) throw new IllegalArgumentException("resource url path must start with /");
        checkInitialize_(false);
        if (!urlPath.endsWith("/")) urlPath = urlPath + "/";
        cacheUrlPath_ = urlPath;
        if (logger_.isDebugEnabled()) logger_.debug(String.format("cache url root set to %1$s", urlPath));
    }
    
    @Override
    public void clearCache() {
        cache_.clear();
        processCache2_.clear();
        processCache_.clear();
    }
    
    private IFileLocator fl_ = FileResource.defFileLocator; 
    @Override
    public void setFileLocator(IFileLocator fileLocator) {
    	if (null == fileLocator) throw new NullPointerException();
    	fl_ = fileLocator;
    }
    
    private IBufferLocator bl_ = new BufferLocator();
    @Override
    public void setBufferLocator(IBufferLocator bufferLocator){
        if (null == bufferLocator) throw new NullPointerException();
        bl_ = bufferLocator;
    }
    
    
    private ConcurrentMap<List<String>, List<String>> processCache_ = new ConcurrentHashMap<List<String>, List<String>>();
    private ConcurrentMap<List<String>, List<String>> processCache2_ = new ConcurrentHashMap<List<String>, List<String>>();
    /**
     * A convention used by this minimizer is resource name suffix with "_bundle". For
     * any resource with the name suffix with "_bundle"
     */
    @Override
    public List<String> process(List<String> resourceNames) {
        checkInitialize_(true);
        if (resourceNames.isEmpty()) return Collections.emptyList();
        if (minimize_) {
            if (useCache_ && processCache_.containsKey(resourceNames)) {
                // !!! cache of the return list instead of minimized file
                return processCache_.get(resourceNames);
            }
            // CDN items will break the resource name list into 
            // separate chunks in order to keep the dependency order
            List<String> retLst = new ArrayList<String>();
            List<String> tmpLst = new ArrayList<String>();
            for (String fn: resourceNames) {
                if (!fn.startsWith("http")) {
                    tmpLst.add(fn);
                } else {
                    if (tmpLst.size() > 0) {
                        retLst.add(minimize_(tmpLst));
                        tmpLst.clear();
                    }
                    retLst.add(fn);
                }
            }
            if (tmpLst.size() > 0) {
                retLst.add(minimize_(tmpLst));
                tmpLst.clear();
            }
            
//            return minimize_(resourceNames);
            processCache_.put(resourceNames, retLst);
            return retLst;
        } else {
            List<String> retLst = processWithoutMinimize(resourceNames);
            return retLst;
        }
    }
    
    @Override
    public List<String> processWithoutMinimize(List<String> resourceNames) {
        checkInitialize_(true);
        if (resourceNames.isEmpty()) return Collections.emptyList();
        if (useCache_ && processCache2_.containsKey(resourceNames)) {
            // !!! cache of the return list instead of minimized file
            return processCache2_.get(resourceNames);
        }
        List<String> l = new ArrayList<String>();
        String urlPath = resourceUrlPath_;
        for (String fn: resourceNames) {
            if (fn.startsWith("http")) l.add(fn); // CDN resource
            else {
                String s = fn.replace(type_.getExtension(), "");
                if (s.equalsIgnoreCase("default") || s.endsWith(IDependenceManager.BUNDLE_SUFFIX)) {
                    continue;
                } else {
                    File f = getFile_(fn);
                    if (null == f || !f.isFile()) {
                        continue;
                    }
                }
                String ext = type_.getExtension();
                fn = fn.endsWith(ext) ? fn : fn + ext; 
                if (fn.startsWith("/")) {
                    if (!fn.startsWith(resourceUrlRoot_)) l.add(resourceUrlRoot_ + fn.replaceFirst("/", ""));
                    else l.add(fn);
                }
                else l.add(urlPath + fn);
            }
        }
        processCache2_.put(resourceNames, l);
        return l;
    }
    
    private String minimize_(List<String> resourceNames) {
        FileCache cache = cache_;
        
//        List<String> l = new ArrayList<String>();
        if (useCache_) {
            String fn = cache.get(resourceNames);
            if (null != fn) {
                if (logger_.isDebugEnabled())
                    logger_.debug("cached file returned: " + fn);
//                l.add(cacheUrlPath_ + fn);
                return cacheUrlPath_ + fn;
                
//                for (String s: resourceNames) {
//                    if (s.startsWith("http")) {
//                        l.add(s);
//                    }
//                }
                
//                return l;
            }
        }
        
        IResource rsrc = newCache_();
        Writer out = null;
        try {
            out = rsrc.getWriter();
            for (String s: resourceNames) {
//                if (s.startsWith("http:")) l.add(s);
                if (s.startsWith("http:")) throw new IllegalArgumentException("CDN resource not expected in miminize method");
                else {
                    if (s.startsWith(resourceUrlPath_)) {
                        s = s.replaceFirst(resourceUrlPath_, "");
                    }
                    File f = getFile_(s);
                    if (null != f && f.exists()) merge_(f, out);
                    else ; // possibly a pseudo or error resource name
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger_.warn("cannot close output in minimizor", e);
                }
            }
        }
        
        String fn = rsrc.getKey();
        // filename always cached without regarding to cache setting
        // this is a good time to remove previous file
        // Note it's absolutely not a good idea to turn cache off
        // and minimize on in a production environment
        cache.put(resourceNames, fn);
//        l.add(cacheUrlPath_ + fn);
//        return l;
        return cacheUrlPath_ + fn;
    }
    
    private void merge_(File file, Writer out) {
        if (logger_.isTraceEnabled()) logger_.trace("starting to minimize resource: " + file.getName());
        
        // possibly due to error or pseudo resource name
        try {
            if (compress_) {
                try {
                    compressor_.compress(file, out);
                } catch (Exception e) {
                    logger_.warn(String.format("error minimizing file %1$s", file.getName()), e);
                    copy_(file, out);
                }
            } else {
                copy_(file, out);
            }
        } catch (IOException e) {
            logger_.warn("error processing javascript file file " + file.getName(), e);
        }
    }

    private File getFile_(String resourceName) {
        String fn = resourceName, ext = type_.getExtension();
        fn = fn.endsWith(ext) ? fn : fn + ext;
        String path;
        if (fn.startsWith("/")) {
            path = (!fn.startsWith(rootDir_)) ? rootDir_ + fn.replaceFirst("/", "") : fn; 
        } else {
            path = rootDir_ + File.separator + resourceDir_ + File.separator + fn;
        }
        return fl_.locate(path);
    }

    private static void copy_(File file, Writer out) throws IOException {
        if (logger_.isTraceEnabled()) logger_.trace(String.format("merging file %1$s ...", file.getName()));
        String line = null;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(file));
            PrintWriter writer = new PrintWriter(out);
            while ((line = r.readLine()) != null) {
                writer.println(line);
            }
        } finally {
            if (null != r) r.close();
        }
    }
    
    private IResource newCache_() {
        if (inMemory_) {
            return bl_.newBuffer(type_.getExtension());
        } else {
            return new FileResource(newCacheFile_());
        }
    }

    private File newCacheFile_() {
        String extension = type_.getExtension(); 
        return cache_.createTempFile(extension);
    }
    
    private void checkInitialize_(boolean initialized) {
        boolean notInited = (resourceDir_ == null || rootDir_ == null || resourceUrlPath_ == null || cache_ == null || cacheUrlPath_ == null); 
        
        if (initialized == notInited) {
            throw new IllegalStateException(initialized ?  "minimizer not initialized" : "minimizer already initialized");
        }
    }

    public ResourceType getType() {
        return type_;
    }

}
