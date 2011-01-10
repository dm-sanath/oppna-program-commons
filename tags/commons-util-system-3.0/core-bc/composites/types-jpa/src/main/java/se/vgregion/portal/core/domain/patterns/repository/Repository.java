/**
 * Copyright 2010 Västra Götalandsregionen
 *
 *   This library is free software; you can redistribute it and/or modify
 *   it under the terms of version 2.1 of the GNU Lesser General Public
 *   License as published by the Free Software Foundation.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the
 *   Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA 02111-1307  USA
 *
 */

package se.vgregion.portal.core.domain.patterns.repository;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import se.vgregion.portal.core.domain.patterns.entity.Entity;

/**
 * @author Anders Asplund - Callista Enterprise
 * @param <T>
 *            The Entity type
 * @param <ID>
 *            The Id type
 * 
 */
public interface Repository<T extends Entity<T, ID>, ID extends Serializable> {
    /**
     * Store <code>object</code> in the database.
     * 
     * @param object
     *            the instance to save in the database
     * @return the object stored in the database
     */
    T persist(T object);

    /**
     * Taken from the EntityManager documentation: Synchronize the persistence context to the underlying database.
     */
    void flush();

    /**
     * Remove <code>object</code> from the database.
     * 
     * @param object
     *            the object to be removed from the database
     */
    void remove(T object);

    /**
     * Delete by primary key.
     * 
     * @param pk
     *            primary key
     * 
     * @deprecated To find an entity by it's id use {@link Repository#deleteById(Serializable)} instead. This
     *             method will be removed in the next version of JpaRepository.
     */
    @Deprecated
    void deleteByPk(ID pk);

    /**
     * Delete by entity ID.
     * 
     * @param id
     *            The id of the entity
     */
    void remove(ID id);

    /**
     * Find all instances of <code>T</code> in the database.
     * 
     * @return a list <code>T</code> objects
     */
    List<T> findAll();

    /**
     * Find instances of <code>T</code> that match the criteria defined by query <code>queryName</code>.
     * <code>args</code> provide the values for any named parameters in the query identified by
     * <code>queryName</code>.
     * 
     * @param queryName
     *            the named query to execute
     * @param args
     *            the values used by the query
     * @return a list of <code>T</code> objects
     */
    List<T> findByNamedQuery(String queryName, Map<String, ? extends Object> args);

    /**
     * Find instances of <code>T</code> that match the criteria defined by query <code>queryName</code>.
     * <code>args</code> provide values for positional arguments in the query identified by <code>queryName</code>.
     * 
     * @param queryName
     *            the named query to execute
     * @param args
     *            the positional values used in the query
     * @return a list of <code>T</code> objects
     */
    List<T> findByNamedQuery(String queryName, Object[] args);

    /**
     * Find a single instance of <code>T</code> using the query named <code>queryName</code> and the arguments
     * identified by <code>args</code>.
     * 
     * @param queryName
     *            the name of the query to use
     * @param args
     *            the arguments for the named query
     * @return T or null if no objects match the criteria if more than one instance is returned.
     */
    T findInstanceByNamedQuery(String queryName, Object[] args);

    /**
     * Find a single instance of <code>T</code> using the query named <code>queryName</code> and the arguments
     * identified by <code>args</code>.
     * 
     * @param queryName
     *            the name of the query to use
     * @param args
     *            a Map holding the named parameters of the query
     * @return T or null if no objects match the criteria if more than one instance is returned.
     */
    T findInstanceByNamedQuery(String queryName, Map<String, ? extends Object> args);

    /**
     * Finds the instance of <code>T</code> identified by <code>pk</code>.
     * 
     * @param pk
     *            The primary key
     * @return an object of <code>T</code>
     * 
     * @deprecated To find an entity by it's id use {@link Repository#findByID(Serializable)} instead. This method
     *             will be removed in the next version of JpaRepository.
     */
    @Deprecated
    T findByPk(ID pk);

    /**
     * Finds the instance of <code>T</code> identified by it's <code>ID</code>.
     * 
     * @param id
     *            The id of the entity
     * 
     * @return an object of <code>T</code>
     */
    T find(ID id);

    /**
     * Taken from the EntityManager documentation: Clear the persistence context, causing all managed entities to
     * become detached. Changes made to entities that have not been flushed to the database will not be persisted.
     */
    void clear();

    /**
     * Update existing <code>object</code>.
     * 
     * @param object
     *            the object to update in the database
     * @return an object of <code>T</code>
     */
    T merge(T object);

    /**
     * Taken from the EntityManager documentation: Refresh the state of the instance from the database, overwriting
     * changes made to the entity, if any.
     * 
     * @param object
     *            the object to refresh
     */
    void refresh(T object);

    /**
     * Check if the entity is available in the EntityManager.
     * 
     * @param entity
     *            the entity object
     * @return true if present
     */
    boolean contains(T entity);

    /**
     * Convenience method that lets you persist or merge an entity transparently depending on its state.
     * 
     * @param entity
     *            the entity
     * @return the stored entity
     */
    T store(T entity);
}