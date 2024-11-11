package com.paypal.sre.cfbt.management.dal;

import com.paypal.sre.cfbt.data.test.Attachment;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.shared.CFBTLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

/**
 * This class is the singleton concrete implementation of the AbstractDAO class
 * that handles {@link Attachment} data operations in the MongoDB "Attachment"
 * collection.
 *
 */
public class AttachmentDAO extends AbstractDAO<Attachment> {

    private static final AttachmentDAO mInstance = new AttachmentDAO("Attachment");
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AttachmentDAO.class);
    
    /**
     * Constructor of singleton instance for this object.
     *
     * @param aCollectionName the name of the collection
     */
    public AttachmentDAO(String aCollectionName) {
        super(aCollectionName, Attachment.class);
    }

    /**
     * Accessor for singleton instance of this object.
     *
     * @return the instance.
     */
    public static AttachmentDAO getInstance() {
        return mInstance;
    }

    /**
     * Reads from target data collection in MongoDB that match with the
     * collection record id.
     *
     * @param c a {@link MongoConnection} object
     * @param aId id for collection record
     * @return the record with specified object id or null if not found.
     */
    @Override
    public Attachment readOne(MongoConnection c, String aId) {

        Attachment attachment = super.readOne(c, aId);
        if (attachment == null) {
            return null;
        }

        // If we are reading the old format of attachment then return it as it is.
        if (attachment.getBinaryContent() == null) {
            return attachment;
        }

        /**
         * For the new format, take the binary data we got from the DB and place
         * it into base64 so that all of the callers don't know that we've made
         * a change.
         */
        boolean isGzippable = false;
        
        if(attachment.getGzippable() != null) {
            isGzippable = attachment.getGzippable();
        }
        
        byte[] attachmentData = (byte[]) attachment.getBinaryContent();

        if (isGzippable) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            GZIPInputStream gzipInputStream = null;
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(attachmentData);
                gzipInputStream = new GZIPInputStream(byteArrayInputStream);
                IOUtils.copy(gzipInputStream, byteArrayOutputStream);
                
                
            } catch (Exception ex) {
                CFBTLogger.logError(logger, AttachmentDAO.class.getCanonicalName(), "Error trying to decompress the gzipped attachment.", ex);
                
            } finally {
                try {
                    gzipInputStream.close();
                    byteArrayOutputStream.close();
                } catch (Exception ex) {
                    CFBTLogger.logError(logger, AttachmentDAO.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }
            attachmentData = byteArrayOutputStream.toByteArray();

        }
        attachment.setContent(DatatypeConverter.printBase64Binary(attachmentData));
        attachment.setBinaryContent(null);
        return attachment;
    }

    /**
     * This method is responsible to convert the attachment contents to binary,
     * GZIP it if needed and then inserts a new record {@link Attachment} data
     * into the MongoDB collection.
     *
     * @param c a {@link MongoConnection} object.
     * @param attachment The {@link Attachment} object
     * @return the ObjectID of the newly inserted record
     */
    @Override
    public String insert(MongoConnection c, Attachment attachment) {

        if (attachment == null) {
            return null;
        }

        long attachmentSize = Long.parseLong(attachment.getSize());
        String attachmentType = attachment.getType();
        byte[] binaryData = DatatypeConverter.parseBase64Binary(attachment.getContent());

        boolean isGZippable = ("text/html".equals(attachmentType) || "text/plain".equals(attachmentType) || "application/x-www-form-urlencoded".equals(attachmentType)
                || "application/json".equals(attachmentType)) && attachmentSize > 100 && binaryData != null;

        if (isGZippable) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            GZIPOutputStream gzipOutputStream = null;
            try {
                byteArrayOutputStream = new ByteArrayOutputStream();
                gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(binaryData);
                IOUtils.copy(byteArrayInputStream, gzipOutputStream);
                
            } catch (Exception ex) {
                CFBTLogger.logError(logger, AttachmentDAO.class.getCanonicalName(), "Error trying to gzip binary content of the attachment.", ex);
                
            } finally {
                try {
                    gzipOutputStream.close();
                    byteArrayOutputStream.close();
                } catch (Exception ex) {
                    CFBTLogger.logError(logger, AttachmentDAO.class.getCanonicalName(), ex.getMessage(), ex);
                }
            }
            
            binaryData = byteArrayOutputStream.toByteArray();
            attachment.setGzippable(true);
            
        } else {
            attachment.setGzippable(false);
        }

        attachment.setBinaryContent(binaryData);
        attachment.setContent(null);
        String id = super.insert(c, attachment);
        return id;
    }

}
